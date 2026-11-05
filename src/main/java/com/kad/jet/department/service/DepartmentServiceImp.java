package com.kad.jet.department.service;

import com.kad.jet.department.dto.*;
import com.kad.jet.department.mapper.DepartmentMapper;
import com.kad.jet.department.model.Department;
import com.kad.jet.department.repository.DepartmentRepository;
import com.kad.jet.notification.event.AssignHODToDepartmentEvent;
import com.kad.jet.notification.event.DepartmentCreatedEvent;
import com.kad.jet.notification.event.DepartmentDeletedEvent;
import com.kad.jet.notification.event.DepartmentUpdatedEvent;
import com.kad.jet.program.model.Program;
import com.kad.jet.program.repository.ProgramRepository;
import com.kad.jet.user.model.UserRole;
import com.kad.jet.user.model._User;
import com.kad.jet.user.repository.UserRepository;
import com.kad.jet.user.utility.RoleChecker;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DepartmentServiceImp implements DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final ProgramRepository programRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    @Override
    public DepartmentResponse createDepartment(_User currentUser, CreateDepartmentRequest departmentRequest) {
        //check role
        if (!isAdmin(currentUser)){
            throw new ResponseStatusException(currentUser == null ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN, "unauthorized request");
        }
        //check if program exits
        Program program = programRepository.findById(departmentRequest.getProgramId())
                .orElseThrow(()-> new ResponseStatusException(HttpStatus.NOT_FOUND, "program does not exits"));
        //create department

        boolean alreadyExistsByName = departmentRepository.existsByNameIgnoreCase(departmentRequest.getName());

        if (alreadyExistsByName){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "department name already exists");
        }

        Department newDepartment = Department
                .builder()
                .createdAt(LocalDateTime.now())
                .name(departmentRequest.getName())
                .isActive(false)
                .program(program)
                .build();

        Department savedDepartment = departmentRepository.save(newDepartment);

        eventPublisher.publishEvent(
                new DepartmentCreatedEvent(
                        savedDepartment.getId(),
                        savedDepartment.getName(),
                        currentUser.getId(),
                        program.getId()
                )
        );

        return DepartmentMapper.toDto(savedDepartment);

    }

    @Transactional
    @Override
    public void softDeleteDepartment(_User currentUser, UUID departmentId) {

        if (!isAdmin(currentUser)) {
            throw new ResponseStatusException(currentUser == null ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN, "unauthorized request");
        }

        Department department = findById(departmentId);

        department.setIsActive(false);

        Department softDelete = departmentRepository.save(department);

        eventPublisher.publishEvent(
                new DepartmentDeletedEvent(
                        softDelete.getId(),
                        currentUser.getId()
                )
        );
    }

    @Transactional
    @Override
    public DepartmentResponse updateDepartment(_User currentUser, UUID departmentId, UpdateDepartmentRequest departmentRequest) {

        if (!isAdmin(currentUser)){
            throw new ResponseStatusException(currentUser == null ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN, "unauthorized request");
        }

        Department department = findById(departmentId);

        if (departmentRequest.getName() != null)
            department.setName(departmentRequest.getName());

        if (departmentRequest.getProgramId() != null){
            Program program = programRepository.findById(departmentRequest.getProgramId())
                    .orElseThrow(()-> new ResponseStatusException(HttpStatus.NOT_FOUND, "program not found"));
            department.setProgram(program);
        }

        Department updatedDepartment = departmentRepository.save(department);

        eventPublisher.publishEvent(
                new DepartmentUpdatedEvent(
                        updatedDepartment.getId(),
                        currentUser.getId()
                )
        );

        return DepartmentMapper.toDto(updatedDepartment);
    }

    @Override
    public DepartmentResponse getDepartment(UUID departmentIid) {
        Department department = departmentRepository.findByIdAndIsActiveTrue(departmentIid)
                .orElseThrow(()-> new ResponseStatusException(HttpStatus.NOT_FOUND, "department not found"));

        return DepartmentMapper.toDto(department);
    }

    @Override
    public DepartmentResponse adminGetDepartment(UUID departmentId, _User currentUser) {

        if (!isAdmin(currentUser)){
            throw new ResponseStatusException(currentUser == null ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN, "unauthorized request");
        }

        Department department = findById(departmentId);

        return DepartmentMapper.toDto(department);

    }

    private boolean isAdmin(_User user) {
        return RoleChecker.hasAnyRole(user,
                UserRole.IT_ADMIN,
                UserRole.SUPER_ADMIN);
    }

    @Override
    public DepartmentPageResponse<DepartmentResponse> adminGetAllDepartment(int size, int page, _User currentUSer) {
        if (!isAdmin(currentUSer)){
            throw new ResponseStatusException(currentUSer == null ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN, "unauthorized request");
        }

        if (size > 10) size = 10;
        if (page < 0 ) page = 0;

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Department> result = departmentRepository.findAll(pageable);

        List<DepartmentResponse> content = result.getContent()
                .stream()
                .map(DepartmentMapper::toDto)
                .toList();

        return new DepartmentPageResponse<>(
                content,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isFirst(),
                result.isLast()
        );
    }

    @Override
    public DepartmentPageResponse<DepartmentResponse> getAllDepartment(int size, int page) {
        if (size > 10) size = 10;
        if (page < 0 ) page = 0;

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Department> result = departmentRepository.findByIsActiveTrue(pageable);

        List<DepartmentResponse> content = result.getContent()
                .stream()
                .map(DepartmentMapper::toDto)
                .toList();

        return new DepartmentPageResponse<>(
                content,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isFirst(),
                result.isLast()
        );
    }

    @Override
    public List<DepartmentResponse> getProgramDepartments(UUID programId) {

        if (!programRepository.existsById(programId)){
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "program does not exists");
        }

        List<Department> findAllByProgram = departmentRepository.findByProgramIdAndIsActiveTrue(programId);

        return findAllByProgram.stream()
                .map(DepartmentMapper::toDto)
                .toList();
    }

    @Override
    public List<DepartmentResponse> adminGetProgramDepartments(UUID programId, _User currentUser) {
        if (!isAdmin(currentUser)){
            throw new ResponseStatusException(currentUser == null ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN, "unauthorized request");
        }

        if (!programRepository.existsById(programId)){
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "program does not exists");
        }

        List<Department> findAllByProgram = departmentRepository.findByProgramId(programId);

        return findAllByProgram.stream()
                .map(DepartmentMapper::toDto)
                .toList();
    }

    @Transactional
    @Override
    public DepartmentResponse assignHod(_User currentUser, UUID departmentId, AssignHodToDepartment assignHodToDepartment) {

        if (!RoleChecker.hasAnyRole(currentUser, UserRole.IT_ADMIN, UserRole.SUPER_ADMIN)){
            throw new ResponseStatusException(currentUser == null ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN, "unauthorized request");
        }

        Department department = findById(departmentId);

        _User hod = userRepository.findById(assignHodToDepartment.getUserId())
                        .orElseThrow(()-> new ResponseStatusException(HttpStatus.NOT_FOUND, "user not found"));

        if (hod.getUserRole() != UserRole.HOD){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "user must be an hod to manage department");
        }

        department.setHod(hod);
        department.setIsActive(true);

        Department updateDepartment = departmentRepository.save(department);

        eventPublisher.publishEvent(
                new AssignHODToDepartmentEvent(
                        updateDepartment.getId(),
                        updateDepartment.getHod().getId(),
                        currentUser.getId()
                )
        );

        return DepartmentMapper.toDto(updateDepartment);
    }

    @Transactional
    @Override
    public DepartmentResponse restoreSoftDeletedDepartment(_User currentUser, UUID departmentId) {
        if (!RoleChecker.hasAnyRole(currentUser, UserRole.IT_ADMIN, UserRole.SUPER_ADMIN)){
            throw new ResponseStatusException(currentUser == null ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN, "unauthorized request");
        }

        Department department = findById(departmentId);

        if (department.getIsActive()){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "department is already active");
        }

        department.setIsActive(true);

        Department activatedDepartment = departmentRepository.save(department);

        return DepartmentMapper.toDto(activatedDepartment);
    }

    private Department findById(UUID departmentId){
        return departmentRepository.findById(departmentId)
                .orElseThrow(()-> new ResponseStatusException(HttpStatus.NOT_FOUND, "department not found"));
    }
}
