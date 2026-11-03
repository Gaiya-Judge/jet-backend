package com.kad.jet.notification.event;

import java.util.UUID;

public record AssignHODToDepartmentEvent(
        UUID departmentId,
        UUID assignHodId,
        UUID actorId
) {
}
