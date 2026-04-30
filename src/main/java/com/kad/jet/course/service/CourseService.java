package com.kad.jet.course.service;

import com.kad.jet.course.dto.*;
import com.kad.jet.user.model._User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface CourseService {

    CourseResponse createCourse(CreateCourseRequest request, _User currentUser);

    CourseResponse getCourseById(UUID id);

    Page<CourseResponse> getActiveCourses(Pageable pageable);

    Page<CourseResponse> getAllCourses(Pageable pageable, _User currentUser);

    List<CourseResponse> getCoursesByDepartment(UUID departmentId);

    List<CourseResponse> getCoursesByLevel(Integer level);

    CourseResponse updateCourse(UUID id, UpdateCourseRequest request, _User currentUser);

    void deleteCourse(UUID id, _User currentUser);

    PrerequisiteResponse addPrerequisite(UUID courseId, PrerequisiteRequest request, _User currentUser);

    void removePrerequisite(UUID courseId, UUID requiredCourseId, _User currentUser);

    List<PrerequisiteResponse> getPrerequisites(UUID courseId);
}
