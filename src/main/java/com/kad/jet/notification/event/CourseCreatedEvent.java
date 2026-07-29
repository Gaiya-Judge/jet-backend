package com.kad.jet.notification.event;

import java.util.UUID;

public record CourseCreatedEvent(
        UUID actorId,
        String courseCode,
        UUID courseId
) {
}
