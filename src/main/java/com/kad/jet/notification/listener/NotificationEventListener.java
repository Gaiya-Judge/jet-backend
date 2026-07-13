package com.kad.jet.notification.listener;

import com.kad.jet.finance.repository.TuitionAccountRepository;
import com.kad.jet.finance.repository.WaiverRepository;
import com.kad.jet.notification.event.*;
import com.kad.jet.notification.model.NotificationType;
import com.kad.jet.notification.service.NotificationService;
import com.kad.jet.registration.repository.RegistrationRepository;
import com.kad.jet.student.repository.GradeImportLogRepository;
import com.kad.jet.user.model._User;
import com.kad.jet.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRegistrationApproved(RegistrationApprovedEvent approvedEvent){

        try {
            _User student = findUser(approvedEvent.studentId());

            notificationService.create(
                    student,
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

            notificationService.create(
                    student,
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
                    NotificationType.PAYMENT_FAILED,
                    "Payment Failed",
                    "Your payment could not be verified. Reason: " +
                            event.reason() + ". Please try again or contact the bursar.",
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

            notificationService.create(
                    student,
                    NotificationType.WAIVER_APPLIED,
                    "Payment Waiver Applied",
                    "A waiver has been applied to your tuition account, " +
                            "reducing your minimum required payment.",
                    "TUITION_ACCOUNT",
                    event.tuitionAccountId()
            );
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

            String action = event.added() ? "added to" : "dropped from";

            notificationService.create(
                    student,
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

            notificationService.create(
                    newUser,
                    NotificationType.ACCOUNT_CREATED,
                    "Welcome to JET",
                    "Your account has been created. " +
                            "Please log in and change your password.",
                    "USER",
                    event.newUserId()
            );
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

            notificationService.create(
                    targetUser,
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

            notificationService.create(
                    student,
                    NotificationType.REGISTRATION_SUBMITTED,
                    "Registration Submitted",
                    "Your registration has been submitted. " +
                            "Please wait for approval from the dean.",
                    "REGISTRATION",
                    event.registrationId()
            );
        } catch (Exception e) {
            log.error("Failed to create notification for " +
                    "RegistrationSubmitteddEvent. studentId: {} error: {}",
                    event.studentId(), e.getMessage());
        }
    }


    private _User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException(
                        "user not found for notification: " + userId));
    }
}
