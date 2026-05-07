package com.kad.jet.course.service;

import com.kad.jet.course.dto.*;
import com.kad.jet.course.model.Course;
import com.kad.jet.course.repository.CourseRepository;
import com.kad.jet.department.repository.DepartmentRepository;
import com.kad.jet.user.model.UserRole;
import com.kad.jet.user.model._User;
import com.kad.jet.user.utility.RoleChecker;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CourseServiceImp implements CourseService {

    private final CourseRepository courseRepository;
    private final DepartmentRepository departmentRepository;

    private static final List<Integer> VALID_LEVELS =
            Arrays.asList(100, 200, 300, 400, 500, 600);

    @Transactional
    @Override
    public CourseResponse createCourse(CreateCourseRequest request, _User currentUser) {
        if (!RoleChecker.hasAnyRole(currentUser, UserRole.SUPER_ADMIN, UserRole.HOD)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "unauthorized request");
        }

        if (!VALID_LEVELS.contains(request.getLevel())){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Level must be one of: 100, 200, 300, 400, 500, 600");
        }

        if (courseRepository.existsByCourseCodeIgnoreCase(request.getCourseCode())){
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "course code already exists " + request.getCourseCode());
        }

        var department = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(()-> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "department not found"));

        Course course = Course
                .builder()
                .courseCode(request.getCourseCode())
                .courseType(request.getCourseType())
                .createdAt(LocalDateTime.now())
                .creditUnits(request.getCreditUnits())
                .department(department)
                .level(request.getLevel())
                .title(request.getTitle())
                .isActive(true)
                .build();
        courseRepository.save(course);

        return toResponse(course);

    }

    @Override
    public CourseResponse getCourseById(UUID id) {
        return toResponse(findById(id));
    }

    @Override
    public Page<CourseResponse> getActiveCourses(Pageable pageable) {
        return courseRepository.findByIsActiveTrue(pageable)
                .map(this::toResponse);
    }

    @Override
    public Page<CourseResponse> getAllCourses(Pageable pageable, _User currentUser) {

        if (!RoleChecker.hasAnyRole(currentUser, UserRole.HOD, UserRole.SUPER_ADMIN, UserRole.IT_ADMIN)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "unauthorized request");
        }
        return courseRepository.findAll(pageable)
                .map(this::toResponse);
    }

    @Override
    public List<CourseResponse> getCoursesByDepartment(UUID departmentId) {
        if (!departmentRepository.existsById(departmentId)){
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "department not found " + departmentId);
        }

        return courseRepository.
                findByDepartmentIdAndIsActiveTrue(departmentId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<CourseResponse> getCoursesByLevel(Integer level) {
        if (!VALID_LEVELS.contains(level)){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "level must be one of: 100, 200, 300,400, 500, 600");
        }

        return courseRepository
                .findByLevelAndIsActiveTrue(level)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    @Override
    public CourseResponse updateCourse(UUID id, UpdateCourseRequest request, _User currentUser) {
        if (!RoleChecker.hasAnyRole(currentUser, UserRole.HOD, UserRole.SUPER_ADMIN)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "unauthorized request");
        }

        Course course = findById(id);

        if (request.getTitle() != null) {
            course.setTitle(request.getTitle());
        }
        if (request.getCreditUnits() != null) {
            course.setCreditUnits(request.getCreditUnits());
        }
        if (request.getCourseType() != null) {
            course.setCourseType(request.getCourseType());
        }

        if (request.getCourseCode() != null){
            course.setCourseCode(request.getCourseCode());
        }
        if (request.getLevel() != null) {
            if (!VALID_LEVELS.contains(request.getLevel())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Level must be one of: 100, 200, 300, 400, 500, 600");
            }
            course.setLevel(request.getLevel());
        }

        return toResponse(courseRepository.save(course));
    }

    @Transactional
    @Override
    public void deleteCourse(UUID id, _User currentUser) {
        if (!RoleChecker.hasAnyRole(currentUser, UserRole.HOD, UserRole.SUPER_ADMIN)){
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "unauthorized request");
        }

        Course course = findById(id);
        course.setIsActive(false);
        courseRepository.save(course);
    }


    private CourseResponse toResponse(Course course){
        return CourseResponse
                .builder()
                .courseType(course.getCourseType())
                .id(course.getId())
                .level(course.getLevel())
                .title(course.getTitle())
                .isActive(course.getIsActive())
                .departmentId(course.getDepartment().getId())
                .createdAt(course.getCreatedAt())
                .courseCode(course.getCourseCode())
                .creditUnits(course.getCreditUnits())
                .departmentName(course.getDepartment().getName())
                .build();
    }

    private Course findById(UUID courseId){
        return courseRepository.findById(courseId)
                .orElseThrow(()-> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "course not found " + courseId));
    }
}
