package com.kad.jet.notification.service;

import com.kad.jet.config.SystemConfigKeys;
import com.kad.jet.config.service.SystemConfigService;
import com.kad.jet.notification.dto.NotificationResponse;
import com.kad.jet.notification.model.Notification;
import com.kad.jet.notification.model.NotificationType;
import com.kad.jet.notification.repository.NotificationRepository;
import com.kad.jet.user.model._User;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationServiceImp implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final SystemConfigService systemConfigService;

    @Override
    @Transactional
    public Notification create(_User recipient, NotificationType type, String title, String message,
                               String relatedEntityType, UUID relatedEntityId) {

        if (!isNotificationsEnabled()){
            return null;
        }

        Notification notification = Notification.builder()
                .recipient(recipient)
                .type(type)
                .title(title)
                .message(message)
                .relatedEntityType(relatedEntityType)
                .relatedEntityId(relatedEntityId)
                .isRead(false)
                .build();

        return notificationRepository.save(notification);
    }

    @Override
    public Page<NotificationResponse> getMyNotifications(_User currentUser, Pageable pageable) {
        return notificationRepository
                .findByRecipientIdOrderByCreatedAtDesc(currentUser.getId(), pageable)
                .map(this::toDto);
    }

    @Override
    public long getUnreadCount(_User currentUser) {
        return notificationRepository.countByRecipientIdAndIsReadFalse(currentUser.getId());
    }

    @Override
    @Transactional
    public NotificationResponse markAsRead(_User currentUser, UUID notificationId) {
        Notification notification = notificationRepository
                .findByIdAndRecipientId(notificationId, currentUser.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Notification not found"
                ));

        if (!Boolean.TRUE.equals(notification.getIsRead())) {
            notification.setIsRead(true);
            notification.setReadAt(LocalDateTime.now());
        }

        return toDto(notificationRepository.save(notification));
    }

    @Override
    @Transactional
    public void markAllAsRead(_User currentUser) {
        notificationRepository.findByRecipientIdAndIsReadFalse(currentUser.getId())
                .forEach(notification -> {
                    notification.setIsRead(true);
                    notification.setReadAt(LocalDateTime.now());
                });
    }

    private boolean isNotificationsEnabled() {
        return Boolean.parseBoolean(
                systemConfigService.getValue(SystemConfigKeys.NOTIFICATIONS_ENABLED));
    }



    private NotificationResponse toDto(Notification notification) {
        return NotificationResponse
                .builder()
                .id(notification.getId())
                .type(notification.getType().name())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .relatedEntityType(notification.getRelatedEntityType())
                .relatedEntityId(notification.getRelatedEntityId())
                .isRead(notification.getIsRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
