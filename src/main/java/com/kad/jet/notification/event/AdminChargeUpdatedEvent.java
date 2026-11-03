package com.kad.jet.notification.event;

import com.kad.jet.semester.model.SemesterType;

import java.util.UUID;

public record AdminChargeUpdatedEvent(
        UUID adminChargeId,
        SemesterType semesterType,
        UUID bursarActorId,
        String adminChargeName
) {
}
