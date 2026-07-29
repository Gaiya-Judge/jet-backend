package com.kad.jet.notification.event;

import java.util.UUID;

public record WaiverAppliedEvent(
        UUID waiverId,
        UUID tuitionAccountId,
        UUID studentId,
        UUID bursarActorId
) {}
