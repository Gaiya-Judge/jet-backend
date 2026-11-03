package com.kad.jet.notification.model;

import com.kad.jet.user.model._User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private _User recipient;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    private String relatedEntityType;

    private UUID relatedEntityId;

    @Column(nullable = false)
    private Boolean isRead = false;

    private LocalDateTime readAt;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
