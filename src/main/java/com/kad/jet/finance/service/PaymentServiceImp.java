package com.kad.jet.finance.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kad.jet.config.PaystackConfig;
import com.kad.jet.config.service.SystemConfigService;
import com.kad.jet.finance.dto.*;
import com.kad.jet.finance.mapper.PaymentMapper;
import com.kad.jet.finance.model.*;
import com.kad.jet.finance.repository.PaymentRepository;
import com.kad.jet.finance.repository.TuitionAccountRepository;
import com.kad.jet.notification.event.PaymentFailedEvent;
import com.kad.jet.notification.event.PaymentVerifiedEvent;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
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
    private final WebhookEventPersistenceService webhookEventPersistenceService;
    private final WebhookEventProcessingService webhookEventProcessingService;
    private final SystemConfigService systemConfigService;
    private final PaystackClient paystackClient;
    private final PaystackConfig paystackConfig;
    private final ObjectMapper objectMapper;
    private final InternalPaymentHelperService internalPaymentHelperService;
    private final TuitionAccountDomainService tuitionAccountDomainService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public InitiatePaymentResponse initiatePayment(_User currentUser, InitiatePaymentRequest request) {

        if (!RoleChecker.is_student(currentUser)) {
            throw new ResponseStatusException(
                    currentUser == null ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN, "unauthorized request");
        }

        // Generate reference before calling Paystack
        // We need it for both the Paystack call and our DB record
        String reference = generatePaymentReference();

        log.info("Student {} initiating payment. Amount: ₦{} " +
                        "Reference: {}",
                currentUser.getId(),
                request.getAmount(),
                reference);

        // Reserve the amount in a short transaction before contacting Paystack.
        tuitionAccountDomainService.reservePaystackPayment(
                currentUser,
                request.getTuitionAccountId(),
                request.getAmount(),
                reference);

        try {
            // Call Paystack OUTSIDE any database transaction
            // External API calls must never hold DB connections open
            Map<String, Object> paystackData =
                    paystackClient.initializeTransaction(
                            currentUser.getEmail(),
                            request.getAmount(),
                            reference);

            // Update the initiated payment with Paystack details if needed, or just return success
            // For now, we just return the Paystack data directly.
            return InitiatePaymentResponse.builder()
                    .authorizationUrl((String) paystackData
                            .get("authorization_url"))
                    .reference(reference)
                    .accessCode((String) paystackData
                            .get("access_code"))
                    .build();

        } catch (Exception e) {
            log.error(
                    "Could not confirm Paystack initialization for reference {}. " +
                            "Keeping the reservation pending for reconciliation: {}",
                    reference,
                    e.getMessage());
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Could not confirm payment initialization with Paystack. " +
                            "The payment status will be checked later.",
                    e);
        }
    }

    @Override
    public void handleWebhook(String paystackSignature, String rawPayload) {

        verifyWebhookSignature(paystackSignature, rawPayload);

        Map<String, Object> payload;
        try {
            payload = objectMapper.readValue(rawPayload, Map.class);
        } catch (Exception exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "invalid webhook payload format",
                    exception);
        }

        Object eventValue = payload.get("event");
        if (!(eventValue instanceof String eventType)
                || eventType.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "webhook event type is required");
        }

        Object dataValue = payload.get("data");
        if (dataValue != null && !(dataValue instanceof Map<?, ?>)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "webhook data must be an object");
        }
        Map<String, Object> data = dataValue instanceof Map<?, ?>
                ? (Map<String, Object>) dataValue : null;
        Object referenceValue = data == null ? null : data.get("reference");
        String reference = referenceValue instanceof String value
                && !value.isBlank() ? value : "UNKNOWN";
        if ("charge.success".equals(eventType)
                && "UNKNOWN".equals(reference)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "charge.success webhook reference is required");
        }

        String eventIdentity = WebhookEventIdentity.create(
                "PAYSTACK", eventType, reference, data, rawPayload);
        WebhookEvent webhookEvent = WebhookEvent.builder()
                .provider("PAYSTACK")
                .eventType(eventType)
                .reference(reference)
                .eventIdentity(eventIdentity)
                .rawPayload(rawPayload)
                .signature(paystackSignature)
                .processed(false)
                .build();

        UUID eventId;
        try {
            eventId = webhookEventPersistenceService.persist(webhookEvent);
        } catch (DataIntegrityViolationException duplicateIdentity) {
            eventId = webhookEventPersistenceService
                    .findIdByEventIdentity(eventIdentity)
                    .orElseThrow(() -> duplicateIdentity);
        }

        try {
            webhookEventProcessingService.processIncomingEvent(eventId);
        } catch (Exception processingException) {
            webhookEventPersistenceService.recordProcessingFailure(
                    eventId, processingException.getMessage());
            log.error("Webhook processing failed for reference {}: {}",
                    reference, processingException.getMessage());
        }
    }

    @Override
    @Transactional
    public PaymentResponse submitBankTransfer(_User currentUser, BankTransferRequest request) {
        if (!RoleChecker.is_student(currentUser)) {
            throw new ResponseStatusException(
                    currentUser == null ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN, "unauthorized request");
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
                .expiresAt(LocalDateTime.now().plusDays(7))
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
                    currentUser == null ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN, "unauthorized request");
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
        tuitionAccountDomainService.updateTuitionAccountAndProcessRegistration(payment.getId());

        eventPublisher.publishEvent(
                new PaymentVerifiedEvent(
                        payment.getId(),
                        payment.getTuitionAccount().getId(),
                        payment.getTuitionAccount().getStudent().getId(),
                        payment.getAmount(),
                        Boolean.TRUE.equals(payment.getAppliedToAccount())
                )
        );

        return PaymentMapper.toDto(payment);
    }

    @Override
    @Transactional
    public PaymentResponse rejectBankTransfer(_User currentUser, UUID paymentId, RejectPaymentRequest request) {
        if (!RoleChecker.is_bursary(currentUser)) {
            throw new ResponseStatusException(
                    currentUser == null ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN, "unauthorized request");
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

        eventPublisher.publishEvent(
                new PaymentFailedEvent(
                        payment.getId(),
                        payment.getTuitionAccount().getStudent().getId(),
                        request.getReason()
                )
        );

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
                    currentUser == null ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN, "unauthorized request");
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
                    currentUser == null ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN, "unauthorized request");
        }

        return paymentRepository
                .findByPaymentMethodAndStatus(
                        PaymentMethod.BANK_TRANSFER,
                        PaymentStatus.PENDING,
                        pageable)
                .map(PaymentMapper::toDto);
    }

    @Override
    @Transactional
    public PaymentResponse verifyPaymentStatus(_User currentUser, String reference) {
       //check authorization
        if (!RoleChecker.is_student(currentUser)){
            throw new ResponseStatusException(
                    currentUser == null ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN,
                    "unauthorized request");
        }

        //find payment record by reference

        Payment payment = paymentRepository.findByIdempotencyKey(reference)
                .orElseThrow(()-> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "payment not found for reference " + reference));

        //rule: student can only verify their payment
        if (!payment.getTuitionAccount().getStudent().getId().equals(currentUser.getId())){
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "this payment those not belong to you"
            );
        }
        //check if already verified
        if (payment.getStatus() == PaymentStatus.VERIFIED) {
            if (!Boolean.TRUE.equals(payment.getAppliedToAccount())) {
                tuitionAccountDomainService
                        .updateTuitionAccountAndProcessRegistration(payment.getId());
            }

            log.info("Payment {} already verified. Returning current state.", reference);
            return PaymentMapper.toDto(payment);
        }

        //check already failed
        if (payment.getStatus() == PaymentStatus.FAILED) {
            return PaymentMapper.toDto(payment);
        }

        // Ask Paystack directly for the truth
        log.info("Checking payment status with Paystack " +
                "for reference: {}", reference);

        try {
            //initiate paystack object data
            Map<String, Object> paystackData =
                    paystackClient.verifyTransaction(reference);

            //fetch status
            String status = (String) paystackData.get("status");

            if ("success".equals(status)){

                //verify the amount matches
                Object amountObj = paystackData.get("amount");
                BigDecimal paidAmountNaira =
                        PaystackAmountConverter.toNaira(amountObj);

                if (paidAmountNaira.compareTo(payment.getAmount()) != 0){
                    log.error("Amount mismatch on manual verify " +
                                    "for reference {}. " +
                                    "Expected: ₦{} Got: ₦{}",
                            reference,
                            payment.getAmount(),
                            paidAmountNaira);
                    payment.setStatus(PaymentStatus.FAILED);
                    paymentRepository.save(payment);
                    return PaymentMapper.toDto(payment);
                }

                // Paystack confirms payment — process it
                payment.setStatus(PaymentStatus.VERIFIED);
                payment.setVerifiedAt(LocalDateTime.now());
                paymentRepository.save(payment);

                tuitionAccountDomainService.updateTuitionAccountAndProcessRegistration(payment.getId());

                eventPublisher.publishEvent(
                        new PaymentVerifiedEvent(
                                payment.getId(),
                                payment.getTuitionAccount().getId(),
                                payment.getTuitionAccount().getStudent().getId(),
                                payment.getAmount(),
                                Boolean.TRUE.equals(payment.getAppliedToAccount())
                        )
                );

                log.info("Payment {} verified via manual check. " +
                        "Amount: ₦{}", reference, paidAmountNaira);
            } else {
                // Paystack says not successful yet
                // Could still be processing — leave as PENDING
                log.info("Paystack reports payment {} not yet " +
                                "successful. Status from Paystack: {}",
                        reference, status);
            }
        } catch (Exception e) {
            log.error("Could not reach Paystack to verify " +
                    "reference {}: {}", reference, e.getMessage());
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Could not determine the payment status with Paystack. " +
                            "Please try again later.",
                    e);
        }

        return PaymentMapper.toDto(payment);
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

}
