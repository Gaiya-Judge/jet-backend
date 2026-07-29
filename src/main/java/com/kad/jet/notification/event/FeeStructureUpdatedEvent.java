package com.kad.jet.notification.event;

import java.util.UUID;

public record FeeStructureUpdatedEvent(
        UUID feeStructureId,
        UUID bursarActorId,
        UUID programId
) {
}
