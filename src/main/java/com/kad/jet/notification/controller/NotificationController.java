package com.kad.jet.notification.controller;

import com.kad.jet.config.SystemConfigKeys;
import com.kad.jet.config.service.SystemConfigService;
import com.kad.jet.notification.dto.NotificationResponse;
import com.kad.jet.notification.service.NotificationService;
import com.kad.jet.user.model._User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("api/notification")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final SystemConfigService systemConfigService;

    @GetMapping
    public ResponseEntity<?> getMyNotifications(
            @AuthenticationPrincipal _User currentUser,
            @PageableDefault(size = 10, sort = "createdAt",
                    direction = Sort.Direction.DESC)
            Pageable pageable) {

        checkFeatureEnabled();

        Page<NotificationResponse> response =
                notificationService.getMyNotifications(currentUser, pageable);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/unread-count")
    public ResponseEntity<?> getUnreadCount(
            @AuthenticationPrincipal _User currentUser) {

        checkFeatureEnabled();

        long unreadCount =
                notificationService.getUnreadCount(currentUser);

        return ResponseEntity.ok(
                Map.of("unreadCount", unreadCount)
        );
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<?> markAsRead(
            @AuthenticationPrincipal _User currentUser,
            @PathVariable UUID id) {

        checkFeatureEnabled();

        NotificationResponse response =
                notificationService.markAsRead(currentUser, id);

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/read-all")
    public ResponseEntity<?> markAllAsRead(
            @AuthenticationPrincipal _User currentUser) {

        checkFeatureEnabled();

        notificationService.markAllAsRead(currentUser);

        return ResponseEntity.noContent().build();
    }

    private void checkFeatureEnabled() {
        boolean enabled = Boolean.parseBoolean(
                systemConfigService.getValue(SystemConfigKeys.NOTIFICATIONS_ENABLED));

        if (!enabled) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }
}
