package com.kad.jet.course.controller;

import com.kad.jet.course.dto.CourseResponse;
import com.kad.jet.course.dto.CreateCourseRequest;
import com.kad.jet.course.dto.UpdateCourseRequest;
import com.kad.jet.course.service.CourseService;
import com.kad.jet.user.model._User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("api/course")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    @PostMapping
    public ResponseEntity<?> createCourse(@RequestBody @Valid CreateCourseRequest createCourseRequest,
                                          @AuthenticationPrincipal _User currentUser){
        CourseResponse response = courseService.createCourse(createCourseRequest, currentUser);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping
    public ResponseEntity<?> getActiveCourses(@PageableDefault(size = 10, sort = "courseCode", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(courseService.getActiveCourses(pageable));
    }

    @GetMapping("/all")
    public ResponseEntity<?> getAllCourses(@PageableDefault(size = 10, sort = "courseCode", direction = Sort.Direction.ASC) Pageable pageable,
                                                              @AuthenticationPrincipal _User currentUser) {
        return ResponseEntity.ok(courseService.getAllCourses(pageable, currentUser));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getCourseById(@PathVariable UUID id) {
        CourseResponse response = courseService.getCourseById(id);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/department/{departmentId}")
    public ResponseEntity<?> getCoursesByDepartment(@PathVariable UUID departmentId) {
        List<CourseResponse> response = courseService.getCoursesByDepartment(departmentId);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/level/{level}")
    public ResponseEntity<?> getCoursesByLevel(@PathVariable Integer level) {
        List<CourseResponse> responses = courseService.getCoursesByLevel(level);

        return ResponseEntity.ok(responses);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateCourse(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCourseRequest request,
            @AuthenticationPrincipal _User currentUser) {
        CourseResponse response = courseService.updateCourse(id, request, currentUser);
        return ResponseEntity.ok(response);
    }


    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCourse(@PathVariable UUID id,
                                          @AuthenticationPrincipal _User currentUser) {
        courseService.deleteCourse(id, currentUser);
        return ResponseEntity.noContent().build();
    }
}
