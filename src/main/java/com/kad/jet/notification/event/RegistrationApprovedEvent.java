package com.kad.jet.notification.event;

import java.util.UUID;

public record RegistrationApprovedEvent(UUID registrationId, UUID studentId) {
}
