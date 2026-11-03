package com.kad.jet.notification.event;

import java.util.UUID;

public record PaymentFailedEvent(
        UUID paymentId,
        UUID studentId,
        String reason
) {}
