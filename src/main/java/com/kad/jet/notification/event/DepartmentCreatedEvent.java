package com.kad.jet.notification.event;

import java.util.UUID;

public record DepartmentCreatedEvent(
        UUID departmentId,
        String departmentNama,
        UUID actorId,
        UUID programId
) {
}
