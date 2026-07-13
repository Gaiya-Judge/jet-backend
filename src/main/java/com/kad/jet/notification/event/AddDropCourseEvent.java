package com.kad.jet.notification.event;

import java.util.UUID;

public record AddDropCourseEvent(
        UUID registrationId,
        UUID studentId,
        String courseCode,
        boolean added
) {
}
