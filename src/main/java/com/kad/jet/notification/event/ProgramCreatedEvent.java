package com.kad.jet.notification.event;

import java.util.UUID;

public record ProgramCreatedEvent(
        UUID programId,
        String programName,
        UUID actorId
) {
}
