package com.kad.jet.notification.service;

import com.kad.jet.notification.dto.NotificationResponse;
import com.kad.jet.notification.model.Notification;
import com.kad.jet.notification.model.NotificationType;
import com.kad.jet.user.model._User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface NotificationService {

    // Called internally by event listeners — NOT exposed via controller
    Notification create(_User recipient, NotificationType type,
                        String title, String message,
                        String relatedEntityType, UUID relatedEntityId);

    Page<NotificationResponse> getMyNotifications(_User currentUser, Pageable pageable);

    long getUnreadCount(_User currentUser);

    NotificationResponse markAsRead(_User currentUser, UUID notificationId);

    void markAllAsRead(_User currentUser);
}
