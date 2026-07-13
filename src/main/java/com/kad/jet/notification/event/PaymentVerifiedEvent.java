package com.kad.jet.notification.event;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentVerifiedEvent(
        UUID paymentId,
        UUID tuitionAccountId,
        UUID studentId,
        BigDecimal amount
)
{}
