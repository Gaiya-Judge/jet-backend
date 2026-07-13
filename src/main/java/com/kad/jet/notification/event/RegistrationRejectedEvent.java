package com.kad.jet.notification.event;

import java.util.UUID;

public record RegistrationRejectedEvent(UUID registrationId, UUID studentId, String remark) {
}
