package com.kad.jet.notification.event;

import com.kad.jet.semester.model.SemesterType;

import java.util.UUID;

public record FeeStructureCreatedEvent(
        UUID feeStructureId,
        UUID programId,
        SemesterType semesterType,
        UUID bursarActorId
) {
}
