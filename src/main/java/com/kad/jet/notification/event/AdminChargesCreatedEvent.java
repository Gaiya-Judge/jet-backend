package com.kad.jet.notification.event;

import com.kad.jet.semester.model.SemesterType;

import java.util.UUID;

public record AdminChargesCreatedEvent(
        UUID adminChargeId,
        SemesterType semesterType,
        UUID bursarActorId,
        String adminChargeName
) {
}
