package com.kad.jet.finance.service;

import com.kad.jet.finance.dto.CreateFeeStructureRequest;
import com.kad.jet.finance.dto.FeeStructureResponse;
import com.kad.jet.finance.dto.UpdateFeeStructureRequest;
import com.kad.jet.finance.mapper.FeeStructureMapper;
import com.kad.jet.finance.model.FeeStructure;
import com.kad.jet.finance.repository.FeeStructureRepository;
import com.kad.jet.notification.event.FeeStructureCreatedEvent;
import com.kad.jet.notification.event.FeeStructureUpdatedEvent;
import com.kad.jet.program.model.Program;
import com.kad.jet.program.repository.ProgramRepository;
import com.kad.jet.semester.model.Semester;
import com.kad.jet.semester.model.SemesterStatus;
import com.kad.jet.semester.repository.SemesterRepository;
import com.kad.jet.user.model.UserRole;
import com.kad.jet.user.model._User;
import com.kad.jet.user.utility.RoleChecker;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FeeStructureServiceImp implements FeeStructureService{

    private final FeeStructureRepository feeStructureRepository;
    private final SemesterRepository semesterRepository;
    private final ProgramRepository programRepository;
    private final ApplicationEventPublisher eventPublisher;


    @Override
    @Transactional
    public FeeStructureResponse createFeeStructure(_User currentUser, CreateFeeStructureRequest structureRequest) {
        //check authorization
        if (!RoleChecker.is_bursary(currentUser)){
            throw new ResponseStatusException(currentUser == null ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN,
                    "unauthorized request");
        }

        //check semester exists
        Semester semester = semesterRepository.findById(structureRequest.getSemesterId())
                .orElseThrow(()-> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "semester not found"));

        //check if semester is active
        if (semester.getStatus() == SemesterStatus.CLOSED){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "can not create fee structure for closed semester");
        }

        //check if program exists
        Program program = programRepository.findById(structureRequest.getProgramId())
                .orElseThrow(()-> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "program not found"));

        boolean feeStructureExists = feeStructureRepository.existsBySemesterIdAndProgramId(structureRequest.getSemesterId(), structureRequest.getProgramId());

        if (feeStructureExists){
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "fee structure already exist for this program and semester");
        }

        FeeStructure feeStructure = FeeStructure
                .builder()
                .createdBy(currentUser)
                .createdAt(LocalDateTime.now())
                .costPerCreditUnit(structureRequest.getCostPerCreditUnit())
                .program(program)
                .semester(semester)
                .build();

        FeeStructure savedFeeStructure = feeStructureRepository.save(feeStructure);

        eventPublisher.publishEvent(
                new FeeStructureCreatedEvent(
                        feeStructure.getId(),
                        program.getId(),
                        semester.getSemesterType(),
                        currentUser.getId()
                )
        );
        return FeeStructureMapper.toDto(savedFeeStructure);
    }

    @Override
    public FeeStructureResponse getFeeStructureResponse(_User currentUser, UUID id) {
        if (!RoleChecker.hasAnyRole(currentUser, UserRole.SUPER_ADMIN, UserRole.ACCOUNTANT, UserRole.BURSAR)){
            throw new ResponseStatusException(currentUser == null ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN,
                    "unauthorized request");
        }
        return FeeStructureMapper.toDto(findById(id));
    }

    @Override
    public Page<FeeStructureResponse> getAllFeeStructureResponse(_User currentUser, Pageable pageable) {
        if (!RoleChecker.hasAnyRole(currentUser, UserRole.ACCOUNTANT, UserRole.SUPER_ADMIN, UserRole.BURSAR)){
            throw new ResponseStatusException(currentUser == null ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN,
                    "unauthorized request");
        }

        return feeStructureRepository.findAll(pageable)
                .map(FeeStructureMapper::toDto);
    }

    @Override
    public List<FeeStructureResponse> getFeeStructureBySemester(UUID semesterId) {

        boolean existsById = semesterRepository.existsById(semesterId);

        if (!existsById){
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "semester not found");
        }

        return feeStructureRepository.findBySemesterId(semesterId)
                .stream()
                .map(FeeStructureMapper::toDto)
                .toList();
    }

    @Override
    @Transactional
    public FeeStructureResponse updateFeeStructure(_User currentUser, UUID feeStructureId, UpdateFeeStructureRequest structureRequest) {
        if (!RoleChecker.is_bursary(currentUser)){
            throw new ResponseStatusException(currentUser == null ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN,
                    "unauthorized request");
        }

        FeeStructure feeStructure = findById(feeStructureId);

        if (feeStructure.getSemester().getStatus() == SemesterStatus.CLOSED){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "can not update fee structure for closed semester");
        }

        feeStructure.setCostPerCreditUnit(structureRequest.getCostPerCreditUnit());
        FeeStructure updatedFeeStructure = feeStructureRepository.save(feeStructure);

        eventPublisher.publishEvent(
                new FeeStructureUpdatedEvent(
                        updatedFeeStructure.getId(),
                        currentUser.getId(),
                        updatedFeeStructure.getProgram().getId()
                )
        );

        return FeeStructureMapper.toDto(updatedFeeStructure);
    }

    @Override
    @Transactional
    public void deleteFeeStructure(_User currentUser, UUID feeStructureId) {

        if (!RoleChecker.is_superAdmin(currentUser)){
            throw new ResponseStatusException(currentUser == null ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN,
                    "unauthorized request");
        }

        FeeStructure feeStructure = findById(feeStructureId);

        feeStructureRepository.delete(feeStructure);
    }

    private FeeStructure findById(UUID feeStructureId){
        return feeStructureRepository.findById(feeStructureId)
                .orElseThrow(()-> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "fee structure not found"));
    }
}
