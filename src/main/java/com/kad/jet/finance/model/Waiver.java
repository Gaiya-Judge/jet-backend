package com.kad.jet.finance.model;

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
@Entity
@Table(name = "waivers")
public class Waiver {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tuition_account_id",
            nullable = false, unique = true)
    private TuitionAccount tuitionAccount;
    // One waiver per tuition account
    // Cannot apply two waivers to the same account

    @Column(nullable = false)
    private Integer allowedPaymentPercentage;
    // The reduced percentage this student is allowed to pay
    // Must be less than the system MIN_PAYMENT_PERCENTAGE
    // e.g. 40 means student can complete with just 40%

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal reducedMinimumPayment;
    // The actual Naira amount derived from the percentage
    // e.g. 40% of ₦141,000 = ₦56,400
    // Stored for quick reference — derived at creation time

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;
    // Mandatory — Bursar must explain why

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by", nullable = false)
    private _User approvedBy;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime approvedAt;
}
