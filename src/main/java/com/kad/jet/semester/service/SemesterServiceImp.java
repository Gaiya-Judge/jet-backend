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

import java.time.LocalDateTime;
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
                semesterRequest.getAcademyYear(), semesterRequest.getSemesterType())){
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A " + semesterRequest.getSemesterType() +
                    " already exists for " +
                    semesterRequest.getAcademyYear());
        }

        Semester semester = Semester
                .builder()
                .semesterType(semesterRequest.getSemesterType())
                .academicYear(semesterRequest.getAcademyYear())
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

        semester.setAcademicYear(updateSemester.getAcademyYear());

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

        if (semester.getStatus() == SemesterStatus.CLOSED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot change status of a closed semester");
        }

        if (semesterStatusRequest.getStatus() == SemesterStatus.ACTIVE) {
            semesterRepository.findByStatus(SemesterStatus.ACTIVE)
                    .ifPresent(existing -> {
                        throw new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "Semester " + existing.getAcademicYear() +
                                        " " + existing.getSemesterType() +
                                        " is already active. " +
                                        "Close it before activating another.");
                    });
        }

        semester.setStatus(semesterStatusRequest.getStatus());
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

        if (!addDropRequest.getAddDropEnd().isAfter(addDropRequest.getAddDropStart())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Add drop end date must be after start date");
        }

        if (semester.getStatus() != SemesterStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Can only configure add/drop window " +
                            "for an active semester");
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
