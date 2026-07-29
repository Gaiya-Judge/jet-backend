package com.kad.jet.notification.event;

import java.util.UUID;

public record ProgramDeletedEvent (
        UUID programId,
        String programName,
        UUID actorId
)
{}
