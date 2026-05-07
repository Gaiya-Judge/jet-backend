package com.kad.jet.student.model;

import com.kad.jet.program.model.Program;
import com.kad.jet.user.model._User;
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
@Table(name = "student_profile")
public class StudentProfile {

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "id")
    private _User user;

    @Column(nullable = false, unique = true)
    private String matricNumber;

//    @ManyToOne(fetch = FetchType.LAZY)
//    @Column
//    private Program program;

    @Column(nullable = false)
    private Integer level;

    @Column(nullable = false)
    private Integer enrollmentYear;

    @CreationTimestamp
    @Column(nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updateAt;
}

