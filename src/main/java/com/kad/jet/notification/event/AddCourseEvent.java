package com.kad.jet.notification.event;

import java.util.UUID;

public record AddCourseEvent(UUID registrationId, UUID studentId, String courseCode) {
}
