package com.kad.jet.notification.event;

import com.kad.jet.semester.model.SemesterStatus;

import java.util.UUID;

public record SemesterUpdatedStatusEvent(
        UUID semesterId,
        SemesterStatus semesterStatus,
        UUID actorId
) {}
