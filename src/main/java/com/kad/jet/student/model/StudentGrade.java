package com.kad.jet.student.model;

import com.kad.jet.course.model.Course;
import com.kad.jet.semester.model.Semester;
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
@Table(name = "student_grades")
public class StudentGrade {


    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private _User student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "semester_id", nullable = false)
    private Semester semester;

    @Column(nullable = false, length = 2)
    private String grade;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "import_batch_id", nullable = false)
    private GradeImportLog importBatch;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
