package com.kad.jet.registration.model;

import com.kad.jet.semester.model.Semester;
import com.kad.jet.user.model._user;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "registrations")
public class Registration {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id")
    private _user student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "semester_id")
    private Semester semester;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RegistrationStatus status;

    @Column(nullable = false)
    private Integer totalCreditUnit = 0;

    private LocalDateTime submittedAt;

    private LocalDateTime deanReviewAt;

    @Column(columnDefinition = "TEXT")
    private String deanRemark;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assisted_by")
    private _user assistedBy;

    @Column(columnDefinition = "TEXT")
    private String assistReason;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updateAt;
}
