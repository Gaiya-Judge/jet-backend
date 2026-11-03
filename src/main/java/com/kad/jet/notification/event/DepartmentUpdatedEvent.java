package com.kad.jet.notification.event;

import java.util.UUID;

public record DepartmentUpdatedEvent(
        UUID departmentId,
        UUID actorId
) {
}
