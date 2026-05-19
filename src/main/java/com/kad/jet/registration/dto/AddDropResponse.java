package com.kad.jet.registration.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class AddDropResponse {

    private RegistrationResponse registration;

    private BigDecimal previousTotalDue;
    private BigDecimal newTotalDue;
    private BigDecimal difference;
    // Positive = student owes more (course added)
    // Negative = credit generated (course dropped)

    private String message;
    // Human readable summary of what happened
    // e.g. "Course CSC301 dropped. ₦12,000 credit added
    //        to your next semester balance."
}
