package com.kad.jet.notification.event;

import java.util.UUID;

public record GradeImportCompletedEvent(
        UUID importLogId,
        UUID importedByUserId,
        int successCount,
        int errorCount
) {
}
