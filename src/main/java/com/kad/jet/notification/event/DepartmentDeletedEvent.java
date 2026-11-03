package com.kad.jet.notification.event;

import java.util.UUID;

public record DepartmentDeletedEvent(
        UUID departmentId,
        UUID actorId
) {
}
