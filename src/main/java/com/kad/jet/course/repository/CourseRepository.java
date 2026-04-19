package com.kad.jet.course.repository;

import com.kad.jet.course.model.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CourseRepository extends JpaRepository<Course, UUID> {


    Optional<Course> findByCourseCode(String courseCode);

    List<Course> findByDepartmentId(UUID departmentId);

    //List<Course> findByLevel(Integer level);

    List<Course> findByIsActiveTrue();
}
