package com.kad.jet.semester.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "semesters")
public class Semester {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String academyYear;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private SemesterType semesterType;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private SemesterStatus status;

    private LocalDate addDropStart;

    private LocalDate addDropEnd;

    private LocalDate registrationOpen;

    @CreationTimestamp
    @Column(nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updateAt;

}
