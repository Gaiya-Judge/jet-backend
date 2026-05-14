package com.kad.jet.finance.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kad.jet.config.PaystackConfig;
import com.kad.jet.finance.dto.*;
import com.kad.jet.finance.model.Payment;
import com.kad.jet.finance.model.TuitionAccount;
import com.kad.jet.finance.model.TuitionAccountStatus;
import com.kad.jet.finance.repository.PaymentRepository;
import com.kad.jet.finance.repository.TuitionAccountRepository;
import com.kad.jet.registration.model.Registration;
import com.kad.jet.registration.model.RegistrationStatus;
import com.kad.jet.registration.repository.RegistrationRepository;
import com.kad.jet.student.repository.StudentProfileRepository;
import com.kad.jet.user.model._User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImp implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final TuitionAccountRepository tuitionAccountRepository;
    private final RegistrationRepository registrationRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final PaystackClient paystackClient;
    private final PaystackConfig paystackConfig;
    private final ObjectMapper objectMapper;

    @Override
    public InitiatePaymentResponse initiatePayment(_User currentUser, InitiatePaymentRequest request) {
        return null;
    }

    @Override
    public void handleWebhook(String paystackSignature, String rawPayload) {

    }

    @Override
    public PaymentResponse submitBankTransfer(_User currentUser, BankTransferRequest request) {
        return null;
    }

    @Override
    public PaymentResponse verifyBankTransfer(_User currentUser, UUID paymentId, VerifyPaymentRequest request) {
        return null;
    }

    @Override
    public PaymentResponse rejectBankTransfer(_User currentUser, UUID paymentId, RejectPaymentRequest request) {
        return null;
    }

    @Override
    public TuitionAccountResponse getTuitionAccount(_User currentUser, UUID tuitionAccountId) {
        return null;
    }

    @Override
    public Page<PaymentResponse> getPendingBankTransfers(_User currentUser, Pageable pageable) {
        return null;
    }

    private void updateTuitionAccount(Payment payment) {

        TuitionAccount account = payment.getTuitionAccount();

        BigDecimal newTotalPaid = account.getTotalPaid()
                .add(payment.getAmount());
        account.setTotalPaid(newTotalPaid);

        BigDecimal newBalance = account.getTotalDue()
                .subtract(newTotalPaid);

        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            newBalance = BigDecimal.ZERO;
        }

        account.setBalance(newBalance);

        if (newBalance.compareTo(BigDecimal.ZERO) == 0) {
            account.setTuitionAccountStatus(TuitionAccountStatus.PAID);
        } else if (newTotalPaid.compareTo(
                account.getMinimumPayment()) >= 0) {
            account.setTuitionAccountStatus(TuitionAccountStatus.PARTIAL);
        }

        tuitionAccountRepository.save(account);

        // Complete registration when threshold is met
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
