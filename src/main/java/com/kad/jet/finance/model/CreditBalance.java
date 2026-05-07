package com.kad.jet.finance.model;

import com.kad.jet.semester.model.Semester;
import com.kad.jet.user.model._User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "credit_balances")
@Entity
public class CreditBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private _User student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_semester_id", nullable = false)
    private Semester sourceSemester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_semester_id")
    private Semester targetSemester;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private Boolean isApplied = false;

    private LocalDateTime appliedAt;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
