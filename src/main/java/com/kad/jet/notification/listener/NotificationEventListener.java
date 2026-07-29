package com.kad.jet.notification.listener;

import com.kad.jet.course.model.Course;
import com.kad.jet.course.repository.CourseRepository;
import com.kad.jet.notification.event.*;
import com.kad.jet.notification.model.NotificationType;
import com.kad.jet.notification.service.NotificationService;
import com.kad.jet.registration.model.Registration;
import com.kad.jet.registration.repository.RegistrationRepository;
import com.kad.jet.user.model.UserRole;
import com.kad.jet.user.model._User;
import com.kad.jet.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final RegistrationRepository registrationRepository;
    private final CourseRepository courseRepository;


    private static final List<UserRole> PROGRAM_STAFF_ROLES =
            List.of(UserRole.DEAN, UserRole.SUPER_ADMIN, UserRole.BURSAR);

    private static final List<UserRole> ADMIN_CHARGES_STAFF_ROLES =
            List.of(UserRole.DEAN, UserRole.SUPER_ADMIN, UserRole.BURSAR, UserRole.ACCOUNTANT, UserRole.REGISTRAR);

    private static final List<UserRole> DEPARTMENT_STAFF_ROLES =
            List.of(UserRole.DEAN, UserRole.IT_ADMIN, UserRole.SUPER_ADMIN);

    private static final List<UserRole> COURSE_ROLES =
            List.of(UserRole.STUDENT, UserRole.SUPER_ADMIN, UserRole.IT_ADMIN, UserRole.HOD);

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRegistrationApproved(RegistrationApprovedEvent approvedEvent){

        try {
            _User student = findUser(approvedEvent.studentId());
            _User actor = findUser(approvedEvent.actorId());

            notificationService.create(
                    student,
                    actor,
                    NotificationType.REGISTRATION_APPROVED,
                    "Registration Approved",
                    "Your registration has been approved by the dean. " +
                    "You can now proceed to pay the tuition fees.",
                    "REGISTRATION",
                    approvedEvent.registrationId()
            );
        }catch (Exception e){
            log.error("Failed to create notification for " +
                    "RegistrationApprovedEvent. registrationId: {} " +
                    "error: {}", approvedEvent.registrationId(), e.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRegistrationRejected(RegistrationRejectedEvent event) {
        try {
            _User student = findUser(event.studentId());
            _User actor = findUser(event.actorId());

            notificationService.create(
                    student,
                    actor,
                    NotificationType.REGISTRATION_REJECTED,
                    "Registration Rejected",
                    "Your course registration was rejected by the Dean. " +
                            "Reason: " + event.remark() +
                            ". Please review and resubmit.",
                    "REGISTRATION",
                    event.registrationId()
            );
        } catch (Exception e) {
            log.error("Failed to create notification for " +
                    "RegistrationRejectedEvent. registrationId: {} " +
                    "error: {}", event.registrationId(), e.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentVerified(PaymentVerifiedEvent event) {
        try {
            _User student = findUser(event.studentId());

            notificationService.create(
                    student,
                    student,
                    NotificationType.PAYMENT_VERIFIED,
                    "Payment Verified",
                    "Your payment of ₦" + event.amount() +
                            " has been verified and applied to your tuition account.",
                    "TUITION_ACCOUNT",
                    event.tuitionAccountId()
            );
        } catch (Exception e) {
            log.error("Failed to create notification for " +
                            "PaymentVerifiedEvent. paymentId: {} error: {}",
                    event.paymentId(), e.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentFailed(PaymentFailedEvent event) {
        try {
            _User student = findUser(event.studentId());

            notificationService.create(
                    student,
                    student,
                    NotificationType.PAYMENT_FAILED,
                    "Payment Failed",
                    "Your payment could not be verified. " +
                            "Please try again or contact the bursar.",
                    "PAYMENT",
                    event.paymentId()
            );
        } catch (Exception e) {
            log.error("Failed to create notification for " +
                            "PaymentFailedEvent. paymentId: {} error: {}",
                    event.paymentId(), e.getMessage());
        }
    }

//    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
//    public void onBankTransferSubmitted(BankTransferSubmittedEvent event) {
//        try {
//            List<_User> bursars = userRepository
//                    .findByUserRoleAndIsActiveTrue(UserRole.BURSAR);
//
//            for (_User bursar : bursars) {
//                notificationService.create(
//                        bursar,
//                        NotificationType.BANK_TRANSFER_SUBMITTED,
//                        "New Bank Transfer Pending Verification",
//                        "A student submitted a bank transfer that requires verification.",
//                        "PAYMENT",
//                        event.paymentId()
//                );
//            }
//        } catch (Exception e) {
//            log.error("Failed to create notification for " +
//                            "BankTransferSubmittedEvent. paymentId: {} error: {}",
//                    event.paymentId(), e.getMessage());
//        }
//    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWaiverApplied(WaiverAppliedEvent event) {
        try {
            _User student = findUser(event.studentId());
            _User bursarActor = findUser(event.bursarActorId());

            notificationService.create(
                    student,
                    bursarActor,
                    NotificationType.WAIVER_APPLIED,
                    "Payment Waiver Applied",
                    "A waiver has been applied to your tuition account, " +
                            "reducing your minimum required payment.",
                    "TUITION_ACCOUNT",
                    event.tuitionAccountId()
            );

            List<_User> superAdminUsers = userRepository
                    .findByUserRoleAndIsActiveTrue(UserRole.SUPER_ADMIN);

            for (_User superAdmin : superAdminUsers){
                notificationService.create(
                        superAdmin,
                        bursarActor,
                        NotificationType.WAIVER_APPLIED,
                        "Waiver Applied",
                        "A waiver has been applied to " + student.getFullName() +
                                "'s tuition account (ID: " + event.tuitionAccountId() + ").",
                        "WAIVER",
                        event.waiverId()
                );
            }
        } catch (Exception e) {
            log.error("Failed to create notification for " +
                            "WaiverAppliedEvent. waiverId: {} error: {}",
                    event.waiverId(), e.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAddDropCourse(AddDropCourseEvent event) {
        try {
            _User student = findUser(event.studentId());
            _User actor = findUser(event.actorId());

            String action = event.added() ? "added to" : "dropped from";

            notificationService.create(
                    student,
                    actor,
                    event.added()
                            ? NotificationType.ADD_DROP_COURSE_ADDED
                            : NotificationType.ADD_DROP_COURSE_DROPPED,
                    "Course " + (event.added() ? "Added" : "Dropped"),
                    "Course " + event.courseCode() + " was " + action +
                            " your registration during the add/drop window.",
                    "REGISTRATION",
                    event.registrationId()
            );
        } catch (Exception e) {
            log.error("Failed to create notification for " +
                            "AddDropCourseEvent. registrationId: {} error: {}",
                    event.registrationId(), e.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGradeImportCompleted(GradeImportCompletedEvent event) {
        try {
            _User importedBy = findUser(event.importedByUserId());

            notificationService.create(
                    importedBy,
                    importedBy,
                    NotificationType.GRADE_IMPORT_COMPLETED,
                    "Grade Import Completed",
                    "Your grade import finished. " +
                            event.successCount() + " succeeded, " +
                            event.errorCount() + " failed.",
                    "GRADE_IMPORT_LOG",
                    event.importLogId()
            );
        } catch (Exception e) {
            log.error("Failed to create notification for " +
                            "GradeImportCompletedEvent. importLogId: {} error: {}",
                    event.importLogId(), e.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAccountCreated(AccountCreatedEvent event) {
        try {
            _User newUser = findUser(event.newUserId());
            _User adminActor = findUser(event.adminActorId());

            notificationService.create(
                    newUser,
                    adminActor,
                    NotificationType.ACCOUNT_CREATED,
                    "Welcome to JET",
                    "Your account has been created. " +
                            "Please log in and change your password.",
                    "USER",
                    event.newUserId()
            );

            List<_User> superAdminUsers = userRepository.findByUserRoleAndIsActiveTrue(UserRole.SUPER_ADMIN);

            log.info("New user registered: " +
                    "user email: {}", newUser.getEmail());

            for (_User superAdmin : superAdminUsers) {
                if (superAdmin.getId().equals(event.adminActorId())){
                    continue;
                }
                notificationService.create(
                        superAdmin,
                        adminActor,
                        NotificationType.ACCOUNT_CREATED,
                        "New user registered",
                        "A new user (" + newUser.getFullName() + ") has been registered into JET",
                        "USER",
                        event.newUserId()
                );

            }
        } catch (Exception e) {
            log.error("Failed to create notification for " +
                            "AccountCreatedEvent. userId: {} error: {}",
                    event.newUserId(), e.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPasswordChangedEventListener(PasswordChangedEvent changedEvent){

        try {
            _User user = findUser(changedEvent.userId());

            notificationService.create(
                    user,
                    user,
                    NotificationType.PASSWORD_CHANGED,
                    "Password Changed",
                    "you have successfully changed your password" +
                            "new password will now be used to authentication",
                    "USER",
                    changedEvent.userId()
            );
        } catch (Exception e) {
            log.error("Failed to create notification for " +
                    "PasswordChangedEvent. userId: {} error: {}",
                    changedEvent.userId(), e.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAdminUpdateUserPasswordEventListener(AdminUpdateUserPasswordEvent passwordEvent){

        try {
            _User targetUser = findUser(passwordEvent.targetUserId());
            _User adminActor = findUser(passwordEvent.adminId());

            notificationService.create(
                    targetUser,
                    adminActor,
                    NotificationType.ADMIN_UPDATE_USER_PASSWORD,
                    "Password changed",
                    "Your password has been changed by admin" +
                            "Note: If you did not request this change, go and visit the school admin" +
                            "else new password will be used for authentication from now.",
                    "USER",
                    passwordEvent.targetUserId()
            );
        } catch (Exception e) {
            log.error("Failed to create notification for " +
                    "AdminUpdatePasswordEvent. targetUserId: {} adminUserId : {} error: {}",
                    passwordEvent.targetUserId(), passwordEvent.adminId(), e.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRegistrationSubmittedEventListener(RegistrationSubmittedEvent event){
        try {
            _User student = findUser(event.studentId());
            Registration registration = findRegistrationById(event.registrationId());

            notificationService.create(
                    student,
                    student,
                    NotificationType.REGISTRATION_SUBMITTED,
                    "Registration Submitted",
                    "Your registration has been submitted. " +
                            "Please wait for approval from the dean.",
                    "REGISTRATION",
                    event.registrationId()
            );

            List<_User> deanUsers = userRepository.findByUserRoleAndIsActiveTrue(UserRole.DEAN);

            for (_User deanUser : deanUsers){
                notificationService.create(
                    deanUser,
                    student,
                    NotificationType.REGISTRATION_SUBMITTED,
                    "Registration Submitted",
                    "Registration for " + student.getFullName() + " has been submitted." +
                            "With Id: " + registration.getId() +
                            "Please review and approve.",
                    "REGISTRATION",
                    event.registrationId()
                );
            }
        } catch (Exception e) {
            log.error("Failed to create notification for " +
                    "RegistrationSubmitteddEvent. studentId: {} error: {}",
                    event.studentId(), e.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSemesterUpdatedStatusEventListener(SemesterUpdatedStatusEvent event){

        try {
            _User actor = findUser(event.actorId());

            List<_User> users = userRepository.findByIsActiveTrue();

            String title;
            String message;

            switch (event.semesterStatus()){
                case ACTIVE -> {
                    title = "New Semester Active";
                    message = "A new semester has begun. Registration is now open";
                }
                case CLOSED -> {
                    title = "Semester Closed";
                    message = "The current semester has been closed";
                }default ->{
                    title = "Semester Status Updated";
                    message = "The semester status has been updated";
                }
            }

            for (_User user : users) {

                if (user.getId().equals(actor.getId())) {
                    continue;
                }

                notificationService.create(
                        user,
                        actor,
                        NotificationType.NEW_SEMESTER_UPDATE,
                        title,
                        message,
                        "SEMESTER",
                        event.semesterId()
                );
            }
        }catch (Exception e){
            log.error("Failed to create notification for " +
                            "semster status update. semesterId : {} semester status : {} error : {}",
                    event.semesterId(), event.semesterStatus(), e.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAddAndDropConfigurationEvent(AddAndDropConfigurationEvent event){

        try {
            _User actor = findUser(event.actorId());

            List<_User> studentUsers = userRepository.findByUserRoleAndIsActiveTrue(UserRole.STUDENT);

            for (_User student : studentUsers){

                notificationService.create(
                        student,
                        actor,
                        NotificationType.ADD_DROP_CONFIGURATION,
                        "Add and Drop Windows opened",
                        "You can now add or drop courses until " +
                                event.endDate() + ". Make changes before the window closes.",
                        "SEMESTER",
                        event.semesterId()
                );
            }
        } catch (Exception e) {
            log.error("Fail to send notification for " +
                    "ADD and DROP. semesterId : {} error : {} ",
                    event.semesterId(), e.getMessage() );
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProgramCreatedEventListener(ProgramCreatedEvent event){

        try {
            _User actor = findUser(event.actorId());

            List<_User> staffs = userRepository.findByUserRoleInAndIsActiveTrue(PROGRAM_STAFF_ROLES);

            for (_User staff : staffs){
                if (actor.getId().equals(staff.getId())){
                    continue;
                }

                notificationService.create(
                        staff,
                        actor,
                        NotificationType.NEW_PROGRAM_CREATED,
                        "New program created",
                        "A program called " + event.programName() + " has been created",
                        "PROGRAM",
                        event.programId()
                );
            }
        } catch (Exception e) {
            log.error("failed to send notification for " +
                            "new program created event. program Name : {} program id : {} error : {}",
                    event.programName(), event.programId(), e.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProgramDeleteEventListener(ProgramDeletedEvent event){
        try {
            _User actor = findUser(event.actorId());

            List<_User> staffs = userRepository.findByUserRoleInAndIsActiveTrue(PROGRAM_STAFF_ROLES);

            for (_User staff : staffs){
                if (actor.getId().equals(staff.getId())){
                    continue;
                }

                notificationService.create(
                        staff,
                        actor,
                        NotificationType.PROGRAM_DELETED,
                        "A program was deleted",
                        "A program called " + event.programName() + " has been deleted",
                        "PROGRAM",
                        event.programId()
                );
            }
        } catch (Exception e) {
            log.error("failed to send notification for " +
                            "program deleted event. program Name : {} program id : {} error : {}",
                    event.programName(), event.programId(), e.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAdminChargeCreatedEventListener(AdminChargesCreatedEvent event){
        try {

            _User bursarActorUser = findUser(event.bursarActorId());

            List<_User> staffs = userRepository.findByUserRoleInAndIsActiveTrue(ADMIN_CHARGES_STAFF_ROLES);

            for (_User staff : staffs){
                if (bursarActorUser.getId().equals(staff.getId())){
                    continue;
                }

                notificationService.create(
                        staff,
                        bursarActorUser,
                        NotificationType.ADMIN_CHARGES_CREATED,
                        "New admin charges created",
                        "Admin charge with name " + event.adminChargeName() + " has been created for "
                        + event.semesterType() + " semester",
                        "ADMINCHARGE",
                        event.adminChargeId()
                );
            }
        } catch (Exception e) {
            log.error("Failed to create notification for " +
                            "admin charge created event. admin charge name : {}  semester type {} error : {}",
                    event.adminChargeName(), event.semesterType(), e.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAdminChargeUpdatedEventListener(AdminChargeUpdatedEvent event){
        try {

            _User bursarActorUser = findUser(event.bursarActorId());

            List<_User> staffs = userRepository.findByUserRoleInAndIsActiveTrue(ADMIN_CHARGES_STAFF_ROLES);

            for (_User staff : staffs){
                if (bursarActorUser.getId().equals(staff.getId())){
                    continue;
                }

                notificationService.create(
                        staff,
                        bursarActorUser,
                        NotificationType.ADMIN_CHARGES_UPDATED,
                        "Admin charges updated",
                        "Admin charge with name " + event.adminChargeName() + " has been updated for "
                                + event.semesterType() + " semester",
                        "ADMINCHARGE",
                        event.adminChargeId()
                );
            }
        } catch (Exception e) {
            log.error("Failed to create notification for " +
                            "admin charge updated event. admin charge name : {}  semester type {} error : {}",
                    event.adminChargeName(), event.semesterType(), e.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAdminChargeDeletedEventListener(AdminChargeDeletedEvent event){

        try {
            _User bursarActorUser = findUser(event.bursarActorId());

            List<_User> staffs = userRepository.findByUserRoleInAndIsActiveTrue(ADMIN_CHARGES_STAFF_ROLES);

            for (_User staff : staffs){
                if (bursarActorUser.getId().equals(staff.getId())){
                    continue;
                }

                notificationService.create(
                        staff,
                        bursarActorUser,
                        NotificationType.ADMIN_CHARGES_DELETED,
                        "Admin charges deleted",
                        "Admin charge with name " + event.adminChargeName() + " has been deleted for "
                                + event.semesterType() + " semester",
                        "ADMINCHARGE",
                        event.adminChargeId()
                );
            }
        } catch (Exception e) {
            log.error("Failed to create notification for " +
                            "admin charge deleted event. admin charge name : {}  semester type {} error : {}",
                    event.adminChargeName(), event.semesterType(), e.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFeeStructureCreatedEventListener(FeeStructureCreatedEvent event){

        try {
            _User bursarActorUser = findUser(event.bursarActorId());

            List<_User> users = userRepository.findByIsActiveTrue();

            for (_User user : users){
                if (user.getId().equals(bursarActorUser.getId())){
                    continue;
                }

                String messsage;

                switch (user.getUserRole()){
                    case STUDENT -> {
                        messsage = "Fee structure configured for the semester. " +
                                "You can start paying tuition fee";
                    }default -> {
                        messsage = "Fee structure configured for the semester.";
                    }
                }

                notificationService.create(
                        user,
                        bursarActorUser,
                        NotificationType.FEE_STRUCTURE_SET,
                        "Fee structure set",
                        messsage,
                        "FEE_STRUCTURE",
                        event.feeStructureId()
                );
            }
        }catch (Exception e){
            log.error("Failed to send notification for " +
                            "fee structure created event. fee structureId : {} error : {}",
                    event.feeStructureId(), e.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFeeStructureUpdatedEventListener(FeeStructureUpdatedEvent event){

        try {

            _User bursarActor = findUser(event.bursarActorId());

            List<_User> users = userRepository.findByIsActiveTrue();

            for (_User user : users){
                if (user.getId().equals(bursarActor.getId())){
                    continue;
                }

                notificationService.create(
                        user,
                        bursarActor,
                        NotificationType.FEE_STRUCTURE_UPDATED,
                        "Fee Structure update",
                        "Fee structure has been updated check new fee breakdown",
                        "FEESTRUCTURE",
                        event.feeStructureId()

                );
            }

        } catch (Exception e) {
            log.error("Failed to send notification for " +
                            "fee structure updated event. feeStructureId : {} error : {}",
                    event.feeStructureId(), e.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReconciliationJobPaymentVerifiedEventListener(ReconciliationJobPaymentVerifiedEvent event){
        try {
            _User student = findUser(event.studentId());

            notificationService.create(
                    student,
                    student,
                    NotificationType.PAYMENT_VERIFIED,
                    "Payment Verified",
                    "Your payment of ₦" + event.amount() +
                            " has been verified and applied to your tuition account.",
                    "TUITION_ACCOUNT",
                    event.tuitionAccountId()
            );
        } catch (Exception e) {
            log.error("Failed to create notification for " +
                            "ReconciliationJobPaymentVerifiedEvent. paymentId: {} error: {}",
                    event.paymentId(), e.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReconciliationJobPaymentFailedEventListener(ReconciliationJobPaymentFailedEvent event){
        try {
            _User student = findUser(event.studentId());

            notificationService.create(
                    student,
                    student,
                    NotificationType.PAYMENT_FAILED,
                    "Payment Failed",
                    "Your payment could not be verified. " +
                            ". Please try again or contact the bursar.",
                    "PAYMENT",
                    event.paymentId()
            );
        } catch (Exception e) {
            log.error("Failed to create notification for " +
                            "ReconciliationJobPaymentFailedEvent. paymentId: {} error: {}",
                    event.paymentId(), e.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDepartmentCreatedEventListener(DepartmentCreatedEvent event){
        try {
            _User actor = findUser(event.actorId());

            List<_User> departmentStaffs = userRepository.findByUserRoleInAndIsActiveTrue(DEPARTMENT_STAFF_ROLES);

            for (_User departmentStaff : departmentStaffs){
                if (departmentStaff.getId().equals(actor.getId())){
                    continue;
                }

                notificationService.create(
                        departmentStaff,
                        actor,
                        NotificationType.DEPARTMENT_CREATED,
                        "New department created",
                        "A new deparment with name " + event.departmentNama() +
                                "has being created",
                        "DEPARTMENT",
                        event.departmentId()
                );
            }
        } catch (Exception e) {
            log.error("Failed to create notification for " +
                    "departmentCreatedEvent. departmentName : {} departmentId : {} error : {}",
                    event.departmentNama(), event.departmentId(), e.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDepartmentUpdatedEventListener(DepartmentUpdatedEvent event){
        try {
            _User actor = findUser(event.actorId());

            List<_User> departmentStaffs = userRepository.findByUserRoleInAndIsActiveTrue(DEPARTMENT_STAFF_ROLES);

            for (_User departmentStaff : departmentStaffs){
                if (departmentStaff.getId().equals(actor.getId())){
                    continue;
                }

                notificationService.create(
                        departmentStaff,
                        actor,
                        NotificationType.DEPARTMENT_UPDATED,
                        "A department updated",
                        "a department has been updated",
                        "DEPARTMENT",
                        event.departmentId()
                );
            }

        } catch (Exception e) {
            log.error("failed to create notification for " +
                            "departmentUpdatedEvent. departmentId : {} error : {}",
                    event.departmentId(), e.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDepartmentDeletedEvent(DepartmentDeletedEvent event){
        try {
            _User actor = findUser(event.actorId());

            List<_User> departmentStaffs = userRepository.findByUserRoleInAndIsActiveTrue(DEPARTMENT_STAFF_ROLES);

            for (_User departmentStaff : departmentStaffs){
                if (departmentStaff.getId().equals(actor.getId())){
                    continue;
                }

                notificationService.create(
                        departmentStaff,
                        actor,
                        NotificationType.DEPARTMENT_DELETED,
                        "Department Deleted/set to in-active",
                        "a department has been set to in-active.",
                        "DEPARTMENT",
                        event.departmentId()
                );
            }
        }catch (Exception e){
            log.error("Failed to send notification for " +
                            " departmentDeletedEvent. departmentId : {} error : {}",
                    event.departmentId(), e.getMessage());
        }
    }

    public void onAssignHodToDepartmentEventListener(AssignHODToDepartmentEvent event){
        try {
            _User actor = findUser(event.actorId());

            List<_User> departmentStaffs = userRepository.findByUserRoleInAndIsActiveTrue(DEPARTMENT_STAFF_ROLES);

            for (_User departmentStaff : departmentStaffs){
                if (departmentStaff.getId().equals(actor.getId())){
                    continue;
                }

                notificationService.create(
                        departmentStaff,
                        actor,
                        NotificationType.HOD_ASSIGNED_TO_DEPARTMENT,
                        "Hod assign to department",
                        "A department has being assigned a HOd",
                        "DEPARTMENT",
                        event.actorId()
                );
            }
        } catch (Exception e) {
            log.error("Failed to send notification for " +
                            "assignedHodToDepartment. hodId : {} departmentId : {} error : {}",
                    event.assignHodId(), event.departmentId(), e.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCourseCreatedEventListener(CourseCreatedEvent event){
        try {
            _User actor = findUser(event.actorId());
            Course course = findCourse(event.courseId());

            List<_User> studentAndStaffs = userRepository.findByUserRoleInAndIsActiveTrue(COURSE_ROLES);

            for (_User studentAndStaff : studentAndStaffs){
                if (studentAndStaff.getId().equals(actor.getId())){
                    continue;
                }

                if ((course.getIsDepartmentRestricted() && course.getDepartment().getHod().equals(course.)))
            }
        }
    }



    private _User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException(
                        "user not found for notification: " + userId));
    }

    private Registration findRegistrationById(UUID id){
        return registrationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "registration not found for notification: " + id
                ));
    }

    private Course findCourse(UUID courseId){
        return courseRepository.findById(courseId)
                .orElseThrow(()->new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "course not found for notification: " + courseId
                ));
    }
}
