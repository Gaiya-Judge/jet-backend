package com.kad.jet.notification.event;

import java.math.BigDecimal;
import java.util.UUID;

public record ReconciliationJobPaymentFailedEvent(
        UUID paymentId,
        UUID studentId
) {
}
