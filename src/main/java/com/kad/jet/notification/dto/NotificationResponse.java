package com.kad.jet.notification.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class NotificationResponse {
    private UUID id;
    private String type;              // enum name as string
    private String title;
    private String message;
    private String relatedEntityType;
    private UUID relatedEntityId;
    private Boolean isRead;
    private LocalDateTime createdAt;
}
