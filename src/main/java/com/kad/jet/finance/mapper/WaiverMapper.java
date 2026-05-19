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
                .waiverType(waiver.getWaiverType())
                .value(waiver.getValue())
                .computedAmount(computedAmount)
                .reason(waiver.getReason())
                .approvedBy(waiver.getApprovedBy().getId())
                .approvedByName(
                        waiver.getApprovedBy().getFullName())
                .approvedAt(waiver.getApprovedAt())
                .build();
        }
}
