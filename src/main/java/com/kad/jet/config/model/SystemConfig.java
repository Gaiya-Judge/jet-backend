package com.kad.jet.config.model;

import com.kad.jet.user.model._user;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "system_config")
public class SystemConfig {

    @Id
    private String configKey;

    @Column(nullable = false)
    private String configValue;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private _user updatedBy;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
