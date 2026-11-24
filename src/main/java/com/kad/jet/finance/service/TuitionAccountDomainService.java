package com.kad.jet.finance.service;

import com.kad.jet.finance.model.Payment;
import com.kad.jet.finance.model.PaymentMethod;
import com.kad.jet.finance.model.PaymentStatus;
import com.kad.jet.finance.model.TuitionAccount;
import com.kad.jet.finance.model.TuitionAccountStatus;
import com.kad.jet.finance.repository.PaymentRepository;
import com.kad.jet.finance.repository.TuitionAccountRepository;
import com.kad.jet.registration.model.Registration;
import com.kad.jet.registration.model.RegistrationStatus;
import com.kad.jet.registration.repository.RegistrationRepository;
import com.kad.jet.user.model._User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TuitionAccountDomainService {

    private final TuitionAccountRepository tuitionAccountRepository;
    private final RegistrationRepository registrationRepository;
    private final PaymentRepository paymentRepository;
    private final EntityManager entityManager;

    @Transactional
    public Payment reservePaystackPayment(
            _User student,
            UUID tuitionAccountId,
            BigDecimal amount,
            String reference) {

        try {
            amount = PaystackAmountConverter.normalizeNairaAmount(amount);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "payment amount must be representable in whole Kobo",
                    exception);
        }

        TuitionAccount account = lockTuitionAccount(tuitionAccountId);
        validatePaystackPaymentRequest(student, account, amount);

        Payment payment = Payment.builder()
                .tuitionAccount(account)
                .idempotencyKey(reference)
                .appliedToAccount(false)
                .amount(amount)
                .paymentMethod(PaymentMethod.PAYSTACK)
                .status(PaymentStatus.PENDING)
                .paystackReference(reference)
                .initiatedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .build();

        paymentRepository.save(payment);
        log.info("Payment record saved. Reference: {} Status: PENDING", reference);
        return payment;
    }

    private TuitionAccount lockTuitionAccount(UUID tuitionAccountId) {
        TuitionAccount account = tuitionAccountRepository
                .findByIdWithLock(tuitionAccountId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "tuition account not found"));
        entityManager.refresh(account, LockModeType.PESSIMISTIC_WRITE);
        return account;
    }

    private void validatePaystackPaymentRequest(
            _User student,
            TuitionAccount account,
            BigDecimal amount) {

        validateAccountOwnerAndStatus(student, account);

        BigDecimal pendingAmount = paymentRepository
                .sumActivePendingAmounts(
                        account.getId(), LocalDateTime.now());
        BigDecimal availableBalance = account.getBalance()
                .subtract(pendingAmount);

        if (availableBalance.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "you have a payment currently being processed. " +
                            "please wait for it to complete or try again " +
                            "in 30 minutes if you did not complete the payment.");
        }

        if (account.getBalance().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "this tuition account has no outstanding balance. " +
                            "current balance is ₦" + account.getBalance());
        }

        if (amount.compareTo(availableBalance) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "payment amount ₦" + amount +
                            " exceeds available balance ₦" +
                            availableBalance);
        }

        if (account.getTotalPaid().compareTo(BigDecimal.ZERO) == 0
                && pendingAmount.compareTo(BigDecimal.ZERO) == 0
                && amount.compareTo(account.getMinimumPayment()) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "minimum payment required is ₦" +
                            account.getMinimumPayment() +
                            ". you attempted to pay ₦" + amount +
                            ". if you have a waiver it has been " +
                            "applied to this amount already.");
        }

        if (account.getTotalPaid().compareTo(BigDecimal.ZERO) > 0
                && amount.compareTo(account.getBalance()) != 0) {
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

    private void validateAccountOwnerAndStatus(
            _User student,
            TuitionAccount account) {

        if (!account.getStudent().getId().equals(student.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "this tuition account does not belong to you");
        }

        if (account.getTuitionAccountStatus() == TuitionAccountStatus.PAID) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "this tuition account is already fully paid");
        }
    }

    @Transactional
    public boolean updateTuitionAccountAndProcessRegistration(UUID paymentId) {

        // 1. Pessimistic Lock: This forces other threads to wait!
        Payment payment = paymentRepository.findByIdWithLock(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        if (Boolean.TRUE.equals(payment.getAppliedToAccount())) {
            log.warn("Payment {} already applied. Skipping.", payment.getPaystackReference());
            return true;
        }

        TuitionAccount account = payment.getTuitionAccount();
        TuitionAccount lockedAccount = tuitionAccountRepository
                .findByRegistrationIdWithLock(account.getRegistration().getId())
                .orElseThrow(() -> new RuntimeException("Tuition account not found"));
        entityManager.refresh(lockedAccount, LockModeType.PESSIMISTIC_WRITE);

        if (lockedAccount.getTuitionAccountStatus() == TuitionAccountStatus.PAID) {
            log.warn("Webhook arrived for already PAID account {}. " +
                            "Reference: {}. No changes made.",
                    lockedAccount.getId(),
                    payment.getPaystackReference());
            return false;
        }

        // totalPaid always reflects actual cash received
        BigDecimal newTotalPaid = lockedAccount.getTotalPaid()
                .add(payment.getAmount());

        lockedAccount.setTotalPaid(newTotalPaid);

        // Balance derived — floors at zero
        BigDecimal newBalance = lockedAccount.getTotalDue()
                .subtract(newTotalPaid);

        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            newBalance = BigDecimal.ZERO;
        }

        lockedAccount.setBalance(newBalance);

        // Determine status
        if (newBalance.compareTo(BigDecimal.ZERO) == 0) {
            // Fully paid
            lockedAccount.setTuitionAccountStatus(TuitionAccountStatus.PAID);

        } else if (newTotalPaid.compareTo(
                lockedAccount.getMinimumPayment()) >= 0) {

            // Met the minimum payment threshold
            // This reads account.getMinimumPayment() directly
            // which already reflects any waiver applied
            // No need to recalculate from system config here
            // If waiver reduced minimumPayment to 40%
            // this check respects that automatically
            lockedAccount.setTuitionAccountStatus(TuitionAccountStatus.PARTIAL);
        }

        tuitionAccountRepository.save(lockedAccount);

        if (lockedAccount.getTuitionAccountStatus() == TuitionAccountStatus.PAID ||
                lockedAccount.getTuitionAccountStatus() ==
                        TuitionAccountStatus.PARTIAL) {

            Registration registration = lockedAccount.getRegistration();
            if (registration.getStatus() == RegistrationStatus.APPROVED) {
                registration.setStatus(RegistrationStatus.COMPLETED);
                registrationRepository.save(registration);
            }
        }

        payment.setAppliedToAccount(true);
        paymentRepository.save(payment);
        return true;
    }
}
