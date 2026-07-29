package com.kad.jet.notification.event;

import java.time.LocalDate;
import java.util.UUID;

public record AddAndDropConfigurationEvent(
        UUID semesterId,
        UUID actorId,
        LocalDate startDate,
        LocalDate endDate

) {
}
