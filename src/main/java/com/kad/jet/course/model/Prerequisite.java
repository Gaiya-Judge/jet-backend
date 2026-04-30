package com.kad.jet.course.model;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "prerequisites")
public class Prerequisite {

    @EmbeddedId
    private PrerequisiteId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("courseId")
    @JoinColumn(name = "course_id")
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("requiredCourseId")
    @JoinColumn(name = "required_course_id")
    private Course requiredCourse;
}
