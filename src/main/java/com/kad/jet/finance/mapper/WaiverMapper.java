package com.kad.jet.finance.mapper;

import com.kad.jet.finance.dto.WaiverResponse;
import com.kad.jet.finance.model.Waiver;

import java.math.BigDecimal;

public class WaiverMapper {

    public static WaiverResponse toDto(Waiver waiver, BigDecimal computedAmount) {

        return WaiverResponse.builder()
                .id(waiver.getId())
                .tuitionAccountId(
                        waiver.getTuitionAccount().getId())
                .studentName(waiver.getTuitionAccount().getStudent().getFullName())
                .reason(waiver.getReason())
                .allowedPaymentPercentage(waiver.getAllowedPaymentPercentage())
                .approvedAt(waiver.getApprovedAt())
                .approvedByName(
                        waiver.getApprovedBy().getFullName())
                .approvedAt(waiver.getApprovedAt())
                .build();
        }
}
