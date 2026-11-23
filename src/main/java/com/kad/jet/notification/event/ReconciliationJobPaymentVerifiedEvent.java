package com.kad.jet.notification.event;

import java.math.BigDecimal;
import java.util.UUID;

public record ReconciliationJobPaymentVerifiedEvent(
        UUID paymentId,
        UUID tuitionAccountId,
        UUID studentId,
        BigDecimal amount,
        boolean appliedToAccount
) {
}
