package com.kad.jet.finance.dto;

import com.kad.jet.finance.model.TuitionAccountStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class TuitionAccountResponse {

    private UUID id;
    private UUID registrationId;
    private UUID studentId;
    private String studentName;
    private String matricNumber;
    private UUID semesterId;
    private String academicYear;
    private BigDecimal tuitionAmount;
    private BigDecimal adminChargesTotal;
    private BigDecimal waiverAmount;
    private BigDecimal creditCarriedForward;
    private BigDecimal totalDue;
    private BigDecimal totalPaid;
    private BigDecimal minimumPayment;
    private BigDecimal balance;
    private TuitionAccountStatus status;
    private List<PaymentResponse> payments;
}
