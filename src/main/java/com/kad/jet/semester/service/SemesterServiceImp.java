package com.kad.jet.semester.service;

import com.kad.jet.semester.dto.*;
import com.kad.jet.semester.mapper.SemesterMapper;
import com.kad.jet.semester.model.Semester;
import com.kad.jet.semester.model.SemesterStatus;
import com.kad.jet.semester.repository.SemesterRepository;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SemesterServiceImp implements SemesterService {

    private final SemesterRepository semesterRepository;

    @Override
    @Transactional
    public SemesterResponse createSemester(_User currentUser, CreateSemesterRequest semesterRequest) {
        if (!isAdmin(currentUser)){
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "unauthorized request");
        }

        if (semesterRepository.existsByAcademicYearAndSemesterType(
                semesterRequest.getAcademicYear(), semesterRequest.getSemesterType())){
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A " + semesterRequest.getSemesterType() +
                    " already exists for " +
                    semesterRequest.getAcademicYear());
        }

        Semester semester = Semester
                .builder()
                .semesterType(semesterRequest.getSemesterType())
                .academicYear(semesterRequest.getAcademicYear())
                .createdAt(LocalDateTime.now())
                .status(SemesterStatus.UPCOMING)
                .build();

        semesterRepository.save(semester);

        return SemesterMapper.toDto(semester);
    }

    @Override
    public SemesterResponse getSemesterById(UUID semesterId) {
        return SemesterMapper.toDto(findById(semesterId));
    }

    @Override
    public SemesterResponse getActiveSemester() {
        return semesterRepository.findByStatus(SemesterStatus.ACTIVE)
                .map(SemesterMapper::toDto)
                .orElseThrow(()->
                        new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "no active semester found"));
    }

    @Override
    public Page<SemesterResponse> getAllSemester(Pageable pageable) {
        return semesterRepository.findAll(pageable)
                .map(SemesterMapper::toDto);
    }

    @Override
    @Transactional
    public SemesterResponse updateSemester(_User currentUser, UUID semesterId, UpdateSemesterRequest updateSemester) {
        if (!isAdmin(currentUser)){
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "unauthorized request");
        }

        Semester semester = findById(semesterId);

        if (semester.getStatus() == SemesterStatus.CLOSED){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "you cannot update a closed semester");
        }

        semester.setAcademicYear(updateSemester.getAcademicYear());

        semesterRepository.save(semester);
        return SemesterMapper.toDto(semester);
    }

    @Override
    @Transactional
    public SemesterResponse updateStatus(_User currentUser, UUID semesterId, SemesterStatusRequest semesterStatusRequest) {

        if (!isAdmin(currentUser)){
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "unauthorized");
        }

        Semester semester = findById(semesterId);
        SemesterStatus newStatus = semesterStatusRequest.getStatus();

        // 1. Skip if no status change is needed
        if (semester.getStatus() == newStatus) {
            return SemesterMapper.toDto(semester);
        }

        // 2. Enforce Logical Transitions
        boolean isValidTransition = (semester.getStatus() == SemesterStatus.UPCOMING && newStatus == SemesterStatus.ACTIVE) ||
                (semester.getStatus() == SemesterStatus.ACTIVE && newStatus == SemesterStatus.CLOSED);

        if (!isValidTransition) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status transition");
        }

        // 3. Conflict Check (Exclude self!)
        if (newStatus == SemesterStatus.ACTIVE) {
            semesterRepository.findByStatus(SemesterStatus.ACTIVE)
                    .ifPresent(existing -> {
                        // Only throw if the found 'existing' semester is NOT the one we are editing
                        if (!existing.getId().equals(semesterId)) {
                            throw new ResponseStatusException(HttpStatus.CONFLICT, "Another semester is already active.");
                        }
                    });
        }

        semester.setStatus(newStatus);
        semesterRepository.save(semester);
        return SemesterMapper.toDto(semester);
    }

    @Override
    @Transactional
    public SemesterResponse configureAddAndDrop(_User currentUser, UUID semesterId, AddDropRequest addDropRequest) {
        if (!RoleChecker.hasAnyRole(currentUser, UserRole.SUPER_ADMIN, UserRole.DEAN)){
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "unauthorized request");
        }

        Semester semester = findById(semesterId);

        LocalDate today = LocalDate.now();

        if (addDropRequest.getAddDropStart() != null &&
                !Objects.equals(addDropRequest.getAddDropStart(), semester.getAddDropStart())) {

            if (addDropRequest.getAddDropStart().isBefore(LocalDate.now())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Add and drop start date cannot be set to a past date");
            }
        }

        if (addDropRequest.getAddDropEnd() != null &&
                !Objects.equals(addDropRequest.getAddDropEnd(), semester.getAddDropEnd())) {

            if (addDropRequest.getAddDropEnd().isBefore(LocalDate.now())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Add and drop end date cannot be set to a past date");
            }
        }

        //Logic check: End must be after Start
        if (!addDropRequest.getAddDropEnd().isAfter(addDropRequest.getAddDropStart())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Add drop end date must be after start date");
        }

        if (semester.getStatus() != SemesterStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Can only configure add/drop window for an active semester");
        }

        semester.setAddDropStart(addDropRequest.getAddDropStart());
        semester.setAddDropEnd(addDropRequest.getAddDropEnd());

        semesterRepository.save(semester);

        return SemesterMapper.toDto(semester);
    }

    private Semester findById(UUID id){
        return semesterRepository.findById(id)
                .orElseThrow(()-> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "semester not found"));
    }

    private boolean isAdmin(_User user) {
        return RoleChecker.hasAnyRole(user,
                UserRole.IT_ADMIN,
                UserRole.SUPER_ADMIN);
    }

}
