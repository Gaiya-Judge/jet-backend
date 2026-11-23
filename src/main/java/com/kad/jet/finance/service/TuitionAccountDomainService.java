package com.kad.jet.finance.service;

import com.kad.jet.finance.model.Payment;
import com.kad.jet.finance.model.TuitionAccount;
import com.kad.jet.finance.model.TuitionAccountStatus;
import com.kad.jet.finance.repository.PaymentRepository;
import com.kad.jet.finance.repository.TuitionAccountRepository;
import com.kad.jet.registration.model.Registration;
import com.kad.jet.registration.model.RegistrationStatus;
import com.kad.jet.registration.repository.RegistrationRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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
