package com.kad.jet.finance.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kad.jet.config.PaystackConfig;
import com.kad.jet.config.SystemConfigKeys;
import com.kad.jet.config.service.SystemConfigService;
import com.kad.jet.finance.dto.*;
import com.kad.jet.finance.mapper.PaymentMapper;
import com.kad.jet.finance.model.*;
import com.kad.jet.finance.repository.PaymentRepository;
import com.kad.jet.finance.repository.TuitionAccountRepository;
import com.kad.jet.finance.repository.WebhookEventRepository;
import com.kad.jet.registration.model.Registration;
import com.kad.jet.registration.model.RegistrationStatus;
import com.kad.jet.registration.repository.RegistrationRepository;
import com.kad.jet.student.model.StudentProfile;
import com.kad.jet.student.repository.StudentProfileRepository;
import com.kad.jet.user.model.UserRole;
import com.kad.jet.user.model._User;
import com.kad.jet.user.utility.RoleChecker;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImp implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final TuitionAccountRepository tuitionAccountRepository;
    private final RegistrationRepository registrationRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final SystemConfigService systemConfigService;
    private final PaystackClient paystackClient;
    private final PaystackConfig paystackConfig;
    private final ObjectMapper objectMapper;

    @Override
    public InitiatePaymentResponse initiatePayment(_User currentUser, InitiatePaymentRequest request) {

        if (!RoleChecker.is_student(currentUser)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "unauthorized request");
        }

        // Read tuition account — no transaction needed for reads
        TuitionAccount tuitionAccount = findTuitionAccount(
                request.getTuitionAccountId());


        // Validate all business rules before calling Paystack
        validatePaymentRequest(currentUser,
                tuitionAccount, request.getAmount());

        // Generate reference before calling Paystack
        // We need it for both the Paystack call and our DB record
        String reference = generatePaymentReference();

        log.info("Student {} initiating payment. Amount: ₦{} " +
                        "Reference: {}",
                currentUser.getId(),
                request.getAmount(),
                reference);

        // Call Paystack OUTSIDE any database transaction
        // External API calls must never hold DB connections open
        Map<String, Object> paystackData =
                paystackClient.initializeTransaction(
                        currentUser.getEmail(),
                        request.getAmount(),
                        reference);

        // SHORT transaction — just save the payment record
        saveInitiatedPayment(
                tuitionAccount,
                request.getAmount(),
                reference);

        return InitiatePaymentResponse.builder()
                .authorizationUrl((String) paystackData
                        .get("authorization_url"))
                .reference(reference)
                .accessCode((String) paystackData
                        .get("access_code"))
                .build();
    }

    @Override
    @Transactional
    public void handleWebhook(String paystackSignature, String rawPayload) {

        // Step 1 — verify signature FIRST
        // If this fails we reject immediately and save nothing
        verifyWebhookSignature(paystackSignature, rawPayload);

        // Step 2 — parse the payload
        Map<String, Object> payload;
        try {
            payload = objectMapper.readValue(rawPayload, Map.class);
        } catch (Exception e) {
            log.error("Failed to parse webhook payload: {}",
                    e.getMessage());
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "invalid webhook payload format");
        }

        String event = (String) payload.get("event");
        Map<String, Object> data =
                (Map<String, Object>) payload.get("data");
        String reference = data != null
                ? (String) data.get("reference") : "UNKNOWN";

        log.info("Webhook received. Event: {} Reference: {}",
                event, reference);

        // Step 3 — persist raw event BEFORE processing
        WebhookEvent webhookEvent = WebhookEvent.builder()
                .provider("PAYSTACK")
                .eventType(event != null ? event : "UNKNOWN")
                .reference(reference)
                .rawPayload(rawPayload)
                .signature(paystackSignature)
                .processed(false)
                .build();

        webhookEventRepository.save(webhookEvent);

        // Step 4 — ignore non-charge events
        // Paystack sends many event types — we only care about
        // successful charges
        if (!"charge.success".equals(event)) {
            log.info("Ignoring webhook event type: {}", event);
            markWebhookProcessed(webhookEvent, null);
            return;
        }

        // Step 5 — process the charge
        try {
            processChargeSuccess(data, reference);
            markWebhookProcessed(webhookEvent, null);
        } catch (Exception e) {
            // Mark as failed but do not re-throw
            // We already returned 200 to Paystack
            // Re-throwing would cause Paystack to retry
            // which could cause duplicate processing
            markWebhookFailed(webhookEvent, e.getMessage());
            log.error("Webhook processing failed for reference " +
                    "{}: {}", reference, e.getMessage());
        }
    }

    @Override
    @Transactional
    public PaymentResponse submitBankTransfer(_User currentUser, BankTransferRequest request) {
        if (!RoleChecker.is_student(currentUser)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "unauthorized request");
        }

        TuitionAccount tuitionAccount = findTuitionAccount(
                request.getTuitionAccountId());

        // Student can only pay their own account
        if (!tuitionAccount.getStudent().getId()
                .equals(currentUser.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "this tuition account does not belong to you");
        }

        if (tuitionAccount.getTuitionAccountStatus()
                == TuitionAccountStatus.PAID) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "this tuition account is already fully paid");
        }

        // Teller number is the idempotency key
        // Same teller number cannot be submitted twice
        // This prevents a student from using one bank payment
        // to clear multiple tuition accounts
        if (paymentRepository.existsByIdempotencyKey(
                request.getTellerNumber())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "a payment with teller number '" +
                            request.getTellerNumber() +
                            "' has already been submitted. " +
                            "if this is an error please contact the bursar.");
        }

        Payment payment = Payment.builder()
                .tuitionAccount(tuitionAccount)
                .idempotencyKey(request.getTellerNumber())
                .amount(request.getAmount())
                .paymentMethod(PaymentMethod.BANK_TRANSFER)
                .status(PaymentStatus.PENDING)
                // Status is PENDING until Bursar manually verifies
                .tellerImageUrl(request.getTellerImageUrl())
                .initiatedAt(LocalDateTime.now())
                .build();

        log.info("Bank transfer submitted by student {}. " +
                        "Teller: {} Amount: ₦{}",
                currentUser.getId(),
                request.getTellerNumber(),
                request.getAmount());

        return PaymentMapper.toDto(paymentRepository.save(payment));
    }

    @Override
    @Transactional
    public PaymentResponse verifyBankTransfer(_User currentUser, UUID paymentId, VerifyPaymentRequest request) {
        if (!RoleChecker.is_bursary(currentUser)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "unauthorized request");
        }

        Payment payment = findPaymentById(paymentId);

        // Can only verify bank transfer payments
        // Paystack payments are verified by webhook — not by Bursar
        if (payment.getPaymentMethod()
                != PaymentMethod.BANK_TRANSFER) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "only bank transfer payments can be " +
                            "manually verified by the bursar. " +
                            "paystack payments are verified automatically.");
        }

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "only PENDING payments can be verified. " +
                            "current status: " + payment.getStatus());
        }

        payment.setStatus(PaymentStatus.VERIFIED);
        payment.setVerifiedBy(currentUser);
        payment.setVerifiedAt(LocalDateTime.now());
        paymentRepository.save(payment);

        log.info("Bank transfer {} verified by Bursar {}. " +
                        "Amount: ₦{}",
                paymentId,
                currentUser.getId(),
                payment.getAmount());

        // Update tuition account balance and
        // complete registration if threshold met
        updateTuitionAccount(payment);

        return PaymentMapper.toDto(payment);
    }

    @Override
    @Transactional
    public PaymentResponse rejectBankTransfer(_User currentUser, UUID paymentId, RejectPaymentRequest request) {
        if (!RoleChecker.is_bursary(currentUser)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "unauthorized request");
        }

        Payment payment = findPaymentById(paymentId);

        if (payment.getPaymentMethod()
                != PaymentMethod.BANK_TRANSFER) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "only bank transfer payments can be rejected");
        }

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "only PENDING payments can be rejected. " +
                            "current status: " + payment.getStatus());
        }

        // We set to FAILED but keep the record permanently
        // The student sees it as rejected and submits a new teller
        // The audit trail shows Bursar rejected it and why
        payment.setStatus(PaymentStatus.FAILED);
        paymentRepository.save(payment);

        log.info("Bank transfer {} rejected by Bursar {}. " +
                        "Reason: {}",
                paymentId,
                currentUser.getId(),
                request.getReason());

        return PaymentMapper.toDto(payment);
    }

    @Override
    public TuitionAccountResponse getTuitionAccount(_User currentUser, UUID tuitionAccountId) {
        if (!RoleChecker.hasAnyRole(currentUser,
                UserRole.STUDENT, UserRole.BURSAR,
                UserRole.ACCOUNTANT, UserRole.SUPER_ADMIN)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "unauthorized request");
        }

        TuitionAccount account = findTuitionAccount(tuitionAccountId);

        // Student can only view their own account
        // Bursar, Accountant, and SUPER_ADMIN can view any account
        if (RoleChecker.is_student(currentUser) &&
                !account.getStudent().getId()
                        .equals(currentUser.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "you can only view your own tuition account");
        }

        List<PaymentResponse> payments = paymentRepository
                .findByTuitionAccountId(tuitionAccountId)
                .stream()
                .map(PaymentMapper::toDto)
                .toList();

        String matricNumber = studentProfileRepository
                .findById(account.getStudent().getId())
                .map(StudentProfile::getMatricNumber)
                .orElse("N/A");

        return PaymentMapper.toDto(account, matricNumber, payments);
    }

    @Override
    public Page<PaymentResponse> getPendingBankTransfers(_User currentUser, Pageable pageable) {
        if (!RoleChecker.is_bursary(currentUser)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "unauthorized request");
        }

        return paymentRepository
                .findByPaymentMethodAndStatus(
                        PaymentMethod.BANK_TRANSFER,
                        PaymentStatus.PENDING,
                        pageable)
                .map(PaymentMapper::toDto);
    }

    private void updateTuitionAccount(Payment payment) {

        TuitionAccount account = payment.getTuitionAccount();

        if (account.getTuitionAccountStatus() == TuitionAccountStatus.PAID) {
            log.warn("Webhook arrived for already PAID account {}. " +
                            "Reference: {}. No changes made.",
                    account.getId(),
                    payment.getPaystackReference());
            return;
        }

        // totalPaid always reflects actual cash received
        BigDecimal newTotalPaid = account.getTotalPaid()
                .add(payment.getAmount());

        account.setTotalPaid(newTotalPaid);

        // Balance derived — floors at zero
        BigDecimal newBalance = account.getTotalDue()
                .subtract(newTotalPaid);

        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            newBalance = BigDecimal.ZERO;
        }

        account.setBalance(newBalance);

        // Determine status
        if (newBalance.compareTo(BigDecimal.ZERO) == 0) {
            // Fully paid
            account.setTuitionAccountStatus(TuitionAccountStatus.PAID);

        } else if (newTotalPaid.compareTo(
                account.getMinimumPayment()) >= 0) {

            // Met the minimum payment threshold
            // This reads account.getMinimumPayment() directly
            // which already reflects any waiver applied
            // No need to recalculate from system config here
            // If waiver reduced minimumPayment to 40%
            // this check respects that automatically
            account.setTuitionAccountStatus(TuitionAccountStatus.PARTIAL);
        }

        tuitionAccountRepository.save(account);

        if (account.getTuitionAccountStatus() == TuitionAccountStatus.PAID ||
                account.getTuitionAccountStatus() ==
                        TuitionAccountStatus.PARTIAL) {
            completeRegistration(account);
        }
    }

    private void completeRegistration(TuitionAccount account) {
        Registration registration = account.getRegistration();

        if (registration.getStatus()
                != RegistrationStatus.COMPLETED) {
            registration.setStatus(RegistrationStatus.COMPLETED);
            registrationRepository.save(registration);
            log.info("Registration {} marked COMPLETED",
                    registration.getId());
        }
    }

    private void verifyWebhookSignature(
            String paystackSignature,
            String rawPayload) {

        try {
            Mac mac = Mac.getInstance("HmacSHA512");

            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    paystackConfig.getSecretKey()
                            .getBytes(StandardCharsets.UTF_8),
                    "HmacSHA512");

            mac.init(secretKeySpec);

            byte[] hashBytes = mac.doFinal(
                    rawPayload.getBytes(StandardCharsets.UTF_8));

            String computedHash = HexFormat.of()
                    .formatHex(hashBytes);

            if (!computedHash.equals(paystackSignature)) {
                log.warn("Invalid Paystack webhook signature");
                throw new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "invalid webhook signature");
            }

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Webhook signature error: {}",
                    e.getMessage());
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "webhook verification failed");
        }
    }

    @Transactional
    protected void saveInitiatedPayment(
            TuitionAccount tuitionAccount,
            BigDecimal amount,
            String reference) {

        Payment payment = Payment.builder()
                .tuitionAccount(tuitionAccount)
                .idempotencyKey(reference)
                .amount(amount)
                .paymentMethod(PaymentMethod.PAYSTACK)
                .status(PaymentStatus.PENDING)
                .paystackReference(reference)
                .initiatedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .build();

        paymentRepository.save(payment);

        log.info("Payment record saved. Reference: {} Status: PENDING",
                reference);
    }

    private String generatePaymentReference() {
        String timestamp = String.valueOf(
                System.currentTimeMillis());
        String randomPart = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .toUpperCase();

        return "JET-" +
                timestamp.substring(timestamp.length() - 8) +
                "-" +
                randomPart.substring(0, 12);
    }

    private void processChargeSuccess(
            Map<String, Object> data,
            String reference) {

        String status = (String) data.get("status");

        // Find the matching payment by reference
        Payment payment = paymentRepository
                .findByIdempotencyKey(reference)
                .orElse(null);

        if (payment == null) {
            log.warn("Webhook received for unknown reference: {}. " +
                    "This could be a test event from Paystack " +
                    "dashboard.", reference);
            return;
        }

        // IDEMPOTENCY CHECK — most important guard
        // If Paystack fires the webhook twice (which it can),
        // the second call finds this condition and exits cleanly.
        // Balance is NOT updated twice. Registration is NOT
        // completed twice. Data stays consistent.
        if (payment.getStatus() == PaymentStatus.VERIFIED) {
            log.info("Payment {} already verified — " +
                            "duplicate webhook ignored. " +
                            "Idempotency working correctly.",
                    reference);
            return;
        }

        // Paystack itself says the payment failed
        if (!"success".equals(status)) {
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            log.warn("Paystack reported payment failure " +
                    "for reference: {}", reference);
            return;
        }

        // Verify the amount Paystack charged matches
        // what we expected. Paystack returns amount in KOBO.
        Object amountObj = data.get("amount");
        BigDecimal paidAmountNaira = BigDecimal
                .valueOf(((Number) amountObj).longValue())
                .divide(BigDecimal.valueOf(100));

        if (paidAmountNaira.compareTo(payment.getAmount()) != 0) {
            log.error("AMOUNT MISMATCH for reference {}. " +
                            "Expected: ₦{} Paystack reported: ₦{}. " +
                            "Marking as FAILED for manual investigation.",
                    reference,
                    payment.getAmount(),
                    paidAmountNaira);
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            return;
        }

        // All checks passed — mark the payment as verified
        payment.setStatus(PaymentStatus.VERIFIED);
        payment.setVerifiedAt(LocalDateTime.now());
        paymentRepository.save(payment);

        // Update tuition account and complete registration
        updateTuitionAccount(payment);

        log.info("Payment {} verified successfully. Amount: ₦{}",
                reference, paidAmountNaira);
    }

    private void validatePaymentRequest(
            _User student,
            TuitionAccount account,
            BigDecimal amount) {

        // Student can only pay their own account
        if (!account.getStudent().getId()
                .equals(student.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "this tuition account does not belong to you");
        }

        // Cannot pay a fully settled account
        if (account.getTuitionAccountStatus() == TuitionAccountStatus.PAID) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "this tuition account is already fully paid");
        }

        BigDecimal pendingAmount = paymentRepository
                .sumActivePendingAmounts(
                        account.getId(),LocalDateTime.now());

        // Available to pay = balance minus what is already
        // in-flight as pending payments
        BigDecimal availableBalance = account.getBalance()
                .subtract(pendingAmount);

        if (availableBalance.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "you have a payment currently being processed. " +
                            "please wait for it to complete or try again " +
                            "in 30 minutes if you did not complete the payment.");
        }

        // In validatePaymentRequest
        // Check balance is actually greater than zero
        if (account.getBalance().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "this tuition account has no outstanding balance. " +
                            "current balance is ₦" + account.getBalance());
        }

        // Amount cannot exceed available balance
        if (amount.compareTo(availableBalance) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "payment amount ₦" + amount +
                            " exceeds available balance ₦" +
                            availableBalance);
        }

        // First payment — must meet the 70% minimum
        // First payment minimum check
        if (account.getTotalPaid()
                .compareTo(BigDecimal.ZERO) == 0
                && pendingAmount.compareTo(BigDecimal.ZERO) == 0) {

            // account.getMinimumPayment() already reflects
            // any waiver the Bursar applied
            // No need to recalculate from system config
            if (amount.compareTo(
                    account.getMinimumPayment()) < 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "minimum payment required is ₦" +
                                account.getMinimumPayment() +
                                ". you attempted to pay ₦" + amount +
                                ". if you have a waiver it has been " +
                                "applied to this amount already.");
            }
        }

        // Subsequent payment — must pay exact remaining balance
        // Business rule: remaining balance paid in full
        // in one transaction
        if (account.getTotalPaid()
                .compareTo(BigDecimal.ZERO) > 0) {

            if (amount.compareTo(account.getBalance()) != 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "remaining balance must be paid in full. " +
                                "outstanding balance is ₦" +
                                account.getBalance() +
                                ". you attempted to pay ₦" + amount +
                                ". contact the bursar if you need " +
                                "special payment arrangements.");
            }
        }

//        // Cannot pay more than what is owed
//        if (amount.compareTo(account.getBalance()) > 0) {
//            throw new ResponseStatusException(
//                    HttpStatus.BAD_REQUEST,
//                    "payment amount ₦" + amount +
//                            " exceeds outstanding balance ₦" +
//                            account.getBalance());
//        }
//
//        // First payment must meet the configured minimum percentage
//        // If any payment has been made before — this check is skipped
//        // The remaining balance can be paid in any amount
//        if (account.getTotalPaid()
//                .compareTo(BigDecimal.ZERO) == 0) {
//
//            int minPercentage = systemConfigService.getIntValue(
//                    SystemConfigKeys.MIN_PAYMENT_PERCENTAGE);
//
//            if (amount.compareTo(
//                    account.getMinimumPayment()) < 0) {
//                throw new ResponseStatusException(
//                        HttpStatus.BAD_REQUEST,
//                        "minimum first payment is ₦" +
//                                account.getMinimumPayment() +
//                                " (" + minPercentage + "% of total due ₦" +
//                                account.getTotalDue() + "). " +
//                                "you attempted to pay ₦" + amount);
//            }
//        }
    }

    private TuitionAccount findTuitionAccount(UUID id) {
        return tuitionAccountRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "tuition account not found"));
    }

    private Payment findPaymentById(UUID id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "payment not found"));
    }

    private void markWebhookProcessed(
            WebhookEvent event, String note) {
        event.setProcessed(true);
        event.setProcessedAt(LocalDateTime.now());
        webhookEventRepository.save(event);
    }

    private void markWebhookFailed(
            WebhookEvent event, String errorMessage) {
        event.setProcessed(false);
        event.setProcessingError(errorMessage);
        webhookEventRepository.save(event);
    }
}
