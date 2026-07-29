package com.kad.jet.finance.service;

import com.kad.jet.finance.dto.AdminChargeResponse;
import com.kad.jet.finance.dto.CreateAdminChargeRequest;
import com.kad.jet.finance.dto.UpdateAdminChargeRequest;
import com.kad.jet.finance.mapper.AdminChargeMapper;
import com.kad.jet.finance.model.AdminCharge;
import com.kad.jet.finance.repository.AdminChargeRepository;
import com.kad.jet.notification.event.AdminChargeDeletedEvent;
import com.kad.jet.notification.event.AdminChargeUpdatedEvent;
import com.kad.jet.notification.event.AdminChargesCreatedEvent;
import com.kad.jet.semester.model.Semester;
import com.kad.jet.semester.model.SemesterStatus;
import com.kad.jet.semester.repository.SemesterRepository;
import com.kad.jet.user.model.UserRole;
import com.kad.jet.user.model._User;
import com.kad.jet.user.utility.RoleChecker;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminChargeImp  implements AdminChargeService{

    private final AdminChargeRepository adminChargeRepository;
    private final SemesterRepository semesterRepository;
    private final ApplicationEventPublisher eventPublisher;


    @Override
    @Transactional
    public AdminChargeResponse createAdminCharge(_User currentUser, CreateAdminChargeRequest request) {
        //check authorization
        if (!RoleChecker.is_bursary(currentUser)){
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "unauthorized request");
        }

        //check semester if present
        Semester semester = semesterRepository.findById(request.getSemesterId())
                .orElseThrow(()-> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "semester not found"));

        //check if semester is not close
        if (semester.getStatus() == SemesterStatus.CLOSED){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "cannot create charge for a closed semester");
        }
        //validate name uniques per semester
        boolean existByNameAndSemester = adminChargeRepository.existsBySemesterIdAndNameIgnoreCase(request.getSemesterId(),
                request.getName());

        if (existByNameAndSemester){
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "a charge with name " + request.getName() +
                    " already exists for this semester");
        }

        AdminCharge adminCharge = AdminCharge
                .builder()
                .createdBy(currentUser)
                .amount(request.getAmount())
                .name(request.getName())
                .semester(semester)
                .createdAt(LocalDateTime.now())
                .build();

        AdminCharge savedAdmin = adminChargeRepository.save(adminCharge);

        eventPublisher.publishEvent(
                new AdminChargesCreatedEvent(
                        savedAdmin.getId(),
                        semester.getSemesterType(),
                        currentUser.getId(),
                        adminCharge.getName()
                )
        );


        return AdminChargeMapper.toDto(savedAdmin);
    }

    @Override
    public AdminChargeResponse getAdminChargeById(UUID id) {
        return AdminChargeMapper.toDto(findById(id));
    }

    @Override
    public List<AdminChargeResponse> getAdminChargesBySemester(UUID semesterId) {

        boolean existsBySemester = semesterRepository.existsById(semesterId);

        if (!existsBySemester){
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "semester not found");
        }

        return adminChargeRepository.findBySemesterId(semesterId)
                .stream()
                .map(AdminChargeMapper::toDto)
                .toList();
    }

    @Override
    @Transactional
    public AdminChargeResponse updateAdminCharge(_User currentUser, UUID id, UpdateAdminChargeRequest request) {

        if (!RoleChecker.is_bursary(currentUser)){
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "unauthorized request");
        }

        AdminCharge adminCharge = findById(id);

        if (adminCharge.getSemester().getStatus() == SemesterStatus.CLOSED){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "you can not update admin charge of closed semester");
        }

        if (request.getName() != null){

            boolean nameConflict = adminChargeRepository.existsBySemesterIdAndNameIgnoreCase(
                    adminCharge.getSemester().getId(),
                    request.getName()
            );

            if (nameConflict){
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "a charge with this name already exists for this semester");
            }

            adminCharge.setName(request.getName());
        }

        if (request.getAmount() != null){
            adminCharge.setAmount(request.getAmount());
        }

        AdminCharge updagtedAdminCharge = adminChargeRepository.save(adminCharge);

        eventPublisher.publishEvent(
                new AdminChargeUpdatedEvent(
                        adminCharge.getId(),
                        adminCharge.getSemester().getSemesterType(),
                        currentUser.getId(),
                        adminCharge.getName()
                )
        );

        return AdminChargeMapper.toDto(updagtedAdminCharge);
    }

    @Override
    @Transactional
    public void deleteAdminCharge(_User currentUser, UUID id) {

        if (!RoleChecker.hasAnyRole(currentUser, UserRole.SUPER_ADMIN, UserRole.BURSAR)){
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "unauthorized request");
        }
        AdminCharge adminCharge = findById(id);
        adminChargeRepository.delete(adminCharge);

        eventPublisher.publishEvent(
                new AdminChargeDeletedEvent(
                        adminCharge.getId(),
                        adminCharge.getSemester().getSemesterType(),
                        currentUser.getId(),
                        adminCharge.getName()
                )
        );
    }

    private AdminCharge findById(UUID adminChargeId){
        return adminChargeRepository.findById(adminChargeId)
                .orElseThrow(()-> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "admin charge not found"));
    }
}
