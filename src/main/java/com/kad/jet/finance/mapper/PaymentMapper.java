package com.kad.jet.finance.mapper;

import com.kad.jet.finance.dto.PaymentResponse;
import com.kad.jet.finance.dto.TuitionAccountResponse;
import com.kad.jet.finance.model.Payment;
import com.kad.jet.finance.model.TuitionAccount;

import java.util.List;

public class PaymentMapper {

    public static PaymentResponse toDto(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .tuitionAccountId(
                        payment.getTuitionAccount().getId())
                .idempotencyKey(payment.getIdempotencyKey())
                .amount(payment.getAmount())
                .paymentMethod(payment.getPaymentMethod())
                .status(payment.getStatus())
                .paystackReference(payment.getPaystackReference())
                .tellerImageUrl(payment.getTellerImageUrl())
                .verifiedBy(payment.getVerifiedBy() != null
                        ? payment.getVerifiedBy().getId() : null)
                .verifiedByName(payment.getVerifiedBy() != null
                        ? payment.getVerifiedBy().getFullName()
                        : null)
                .verifiedAt(payment.getVerifiedAt())
                .initiatedAt(payment.getInitiatedAt())
                .createdAt(payment.getCreatedAt())
                .build();
    }

    public static TuitionAccountResponse toDto(
            TuitionAccount account,
            String matricNumber,
            List<PaymentResponse> payments) {

        return TuitionAccountResponse.builder()
                .id(account.getId())
                .registrationId(account.getRegistration().getId())
                .studentId(account.getStudent().getId())
                .studentName(account.getStudent().getFullName())
                .matricNumber(matricNumber)
                .semesterId(account.getSemester().getId())
                .academicYear(account.getSemester().getAcademicYear())
                .tuitionAmount(account.getTuitionAmount())
                .adminChargesTotal(account.getAdminChargesTotal())
                .waiverAmount(account.getWaiverAmount())
                .creditCarriedForward(
                        account.getCreditCarriedForward())
                .totalDue(account.getTotalDue())
                .totalPaid(account.getTotalPaid())
                .minimumPayment(account.getMinimumPayment())
                .balance(account.getBalance())
                .status(account.getTuitionAccountStatus())
                .payments(payments)
                .build();
    }
}
