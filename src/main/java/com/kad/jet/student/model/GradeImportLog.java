package com.kad.jet.student.model;

import com.kad.jet.user.model._user;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.scheduling.annotation.EnableAsync;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "grade_import_logs")
public class GradeImportLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "imported_by", nullable = false)
    private _user importedBy;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private Integer totalRecords = 0;

    @Column(nullable = false)
    private Integer successCount = 0;

    @Column(nullable = false)
    private Integer errorCount = 0;

    @Column(columnDefinition = "TEXT")
    private String errorDetails;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime importedAt;
}
