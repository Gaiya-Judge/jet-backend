package com.kad.jet.program.service;

import com.kad.jet.notification.event.ProgramCreatedEvent;
import com.kad.jet.notification.event.ProgramDeletedEvent;
import com.kad.jet.program.dto.CreateProgramRequest;
import com.kad.jet.program.dto.ProgramPageResponse;
import com.kad.jet.program.dto.ProgramResponse;
import com.kad.jet.program.dto.UpdateProgramRequest;
import com.kad.jet.program.mapper.ProgramMapper;
import com.kad.jet.program.model.Program;
import com.kad.jet.program.repository.ProgramRepository;
import com.kad.jet.user.model.UserRole;
import com.kad.jet.user.model._User;
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
public class ProgramServiceImp implements ProgramService {

    private final ProgramRepository programRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    @Override
    public ProgramResponse createNewProgram(_User currentUser, CreateProgramRequest programRequest) {
       //check role permission
        if (!RoleChecker.hasAnyRole(currentUser, UserRole.IT_ADMIN, UserRole.SUPER_ADMIN)){
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "unauthorized request");
        }
        //check if it already exits
        boolean alreadyExists = programRepository.existsByName(programRequest.getName());

        if (alreadyExists){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "program already exits");
        }

        Program newProgram = Program
                .builder()
                .name(programRequest.getName())
                .levelType(programRequest.getProgramLevelType())
                .createdAt(LocalDateTime.now())
                .isActive(true)
                .build();

        Program savedProgram = programRepository.save(newProgram);

        eventPublisher.publishEvent(
                new ProgramCreatedEvent(
                        savedProgram.getId(),
                        savedProgram.getName(),
                        currentUser.getId()
                )
        );

        return ProgramMapper.toDto(savedProgram);

    }

    @Transactional
    @Override
    public void deleteProgram(_User currentUser, UUID programId) {
        if (!RoleChecker.hasAnyRole(currentUser, UserRole.IT_ADMIN, UserRole.SUPER_ADMIN)){
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "unauthorized request");
        }

        Program program = findById(programId);

        if (!program.getIsActive()){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "program is not active");
        }

        program.setIsActive(false);

        programRepository.save(program);

        eventPublisher.publishEvent(
                new ProgramDeletedEvent(
                        program.getId(),
                        program.getName(),
                        currentUser.getId()
                )
        );
    }

    @Override
    public ProgramResponse getActiveProgramById(UUID programId) {
        Program program = findById(programId);

        return ProgramMapper.toDto(program);
    }

    @Override
    public ProgramResponse getProgramById(UUID programId, _User currentUser) {
        if (!RoleChecker.hasAnyRole(currentUser, UserRole.SUPER_ADMIN, UserRole.IT_ADMIN)){
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "unauthorized request");
        }
        Program program = programRepository.findById(programId)
                .orElseThrow(()-> new ResponseStatusException(HttpStatus.NOT_FOUND, "program not found"));

        return ProgramMapper.toDto(program);
    }

    @Transactional
    @Override
    public ProgramResponse updateProgram(_User currentUser, UUID programId, UpdateProgramRequest programRequest) {

        if (!RoleChecker.hasAnyRole(currentUser, UserRole.IT_ADMIN, UserRole.SUPER_ADMIN)){
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "unauthorized request");
        }

        Program program = findById(programId);

        if (programRequest.getName() != null)
            program.setName(programRequest.getName());
        if (programRequest.getProgramLevelType() != null)
            program.setLevelType(programRequest.getProgramLevelType());

        program.setUpdateAt(LocalDateTime.now());

        Program updatedProgram = programRepository.save(program);

        return ProgramMapper.toDto(updatedProgram);
    }

    @Override
    public ProgramPageResponse<ProgramResponse> getAllActiveProgram(int page, int size) {

        if (size > 10) size = 10;
        if (page < 0) page = 0;

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Program> result = programRepository.findByIsActiveTrue(pageable);

        List<ProgramResponse> content = result
                .getContent()
                .stream()
                .map(ProgramMapper::toDto)
                .toList();

        return new ProgramPageResponse<>(
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
    public ProgramPageResponse<ProgramResponse> getAllProgram(int page, int size, _User currentUser) {

        if (!RoleChecker.hasAnyRole(currentUser, UserRole.IT_ADMIN, UserRole.SUPER_ADMIN)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "unauthorized request");
        }
        if (size > 10) size = 10;
        if (page < 0) page = 0;

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Program> result = programRepository.findAll(pageable);

        List<ProgramResponse> content = result
                .getContent()
                .stream()
                .map(ProgramMapper::toDto)
                .toList();

        return new ProgramPageResponse<>(
                content,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isFirst(),
                result.isLast()
        );
    }

    private Program findById(UUID programId){
        return programRepository.findByIdAndIsActive(programId,true)
                .orElseThrow(()-> new ResponseStatusException(HttpStatus.NOT_FOUND, "program not found"));
    }
}
