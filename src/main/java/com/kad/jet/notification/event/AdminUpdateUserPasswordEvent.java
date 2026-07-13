package com.kad.jet.notification.event;

import java.util.UUID;

public record AdminUpdateUserPasswordEvent(UUID targetUserId, UUID adminId) {
}
