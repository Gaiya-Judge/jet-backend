package com.kad.jet.notification.event;

import java.util.UUID;

public record PasswordChangedEvent(UUID userId) {
}
