package com.kad.jet.notification.event;

import java.util.UUID;

public record RegistrationCreatedEvent(UUID registrationId, UUID studentId) {
}
