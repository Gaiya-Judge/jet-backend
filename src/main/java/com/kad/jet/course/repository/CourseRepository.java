package com.kad.jet.course.repository;

import com.kad.jet.course.model.Course;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CourseRepository extends JpaRepository<Course, UUID> {

    Optional<Course> findByCourseCodeIgnoreCase(String courseCode);

    Page<Course> findByIsActiveTrue(Pageable pageable);

    List<Course> findByDepartmentIdAndIsActiveTrue(UUID departmentId);

    List<Course> findByLevelAndIsActiveTrue(Integer level);

    boolean existsByCourseCodeIgnoreCase(String courseCode);
}
