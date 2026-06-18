package com.kad.jet.report.service;

import com.kad.jet.finance.model.TuitionAccount;
import com.kad.jet.finance.model.TuitionAccountStatus;
import com.kad.jet.finance.model.Waiver;
import com.kad.jet.finance.model.WaiverType;
import com.kad.jet.finance.repository.TuitionAccountRepository;
import com.kad.jet.finance.repository.WaiverRepository;
import com.kad.jet.registration.model.Registration;
import com.kad.jet.registration.model.RegistrationItem;
import com.kad.jet.registration.model.RegistrationStatus;
import com.kad.jet.registration.repository.RegistrationItemRepository;
import com.kad.jet.registration.repository.RegistrationRepository;
import com.kad.jet.report.dto.*;
import com.kad.jet.semester.model.Semester;
import com.kad.jet.semester.repository.SemesterRepository;
import com.kad.jet.student.model.StudentProfile;
import com.kad.jet.student.repository.StudentProfileRepository;
import com.kad.jet.user.model.UserRole;
import com.kad.jet.user.model._User;
import com.kad.jet.user.utility.RoleChecker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImp implements ReportService {

    private final TuitionAccountRepository tuitionAccountRepository;
    private final RegistrationRepository registrationRepository;
    private final RegistrationItemRepository registrationItemRepository;
    private final WaiverRepository waiverRepository;
    private final SemesterRepository semesterRepository;
    private final StudentProfileRepository studentProfileRepository;

    @Override
    public PaymentSummaryReport getPaymentSummary(_User currentUser, UUID semesterId) {

        if (!RoleChecker.hasAnyRole(currentUser,
                UserRole.BURSAR, UserRole.ACCOUNTANT,
                UserRole.SUPER_ADMIN)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "unauthorized request");
        }

        Semester semester = findSemester(semesterId);

        List<TuitionAccount> accounts = tuitionAccountRepository
                .findBySemesterId(semesterId);

        if (accounts.isEmpty()) {
            return PaymentSummaryReport.builder()
                    .academicYear(semester.getAcademicYear())
                    .semesterType(semester.getSemesterType().name())
                    .totalStudents(0)
                    .totalExpected(BigDecimal.ZERO)
                    .totalCollected(BigDecimal.ZERO)
                    .totalOutstanding(BigDecimal.ZERO)
                    .totalWaivers(BigDecimal.ZERO)
                    .fullyPaidCount(0)
                    .partiallyPaidCount(0)
                    .unpaidCount(0)
                    .studentBreakdown(List.of())
                    .build();
        }

        // Aggregate totals across all accounts
        BigDecimal totalExpected = accounts.stream()
                .map(TuitionAccount::getTotalDue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCollected = accounts.stream()
                .map(TuitionAccount::getTotalPaid)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalOutstanding = totalExpected
                .subtract(totalCollected);

        BigDecimal totalWaivers = accounts.stream()
                .map(TuitionAccount::getWaiverAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Count by payment status
        long fullyPaid = accounts.stream()
                .filter(a -> a.getTuitionAccountStatus() ==
                        TuitionAccountStatus.PAID)
                .count();

        long partiallyPaid = accounts.stream()
                .filter(a -> a.getTuitionAccountStatus() ==
                        TuitionAccountStatus.PARTIAL ||
                        a.getTuitionAccountStatus() ==
                                TuitionAccountStatus.ADJUSTED)
                .count();

        long unpaid = accounts.stream()
                .filter(a -> a.getTuitionAccountStatus() ==
                        TuitionAccountStatus.PENDING)
                .count();

        // Build per student breakdown
        List<StudentPaymentSummary> breakdown = accounts.stream()
                .map(this::toStudentPaymentSummary)
                .toList();

        return PaymentSummaryReport.builder()
                .academicYear(semester.getAcademicYear())
                .semesterType(semester.getSemesterType().name())
                .totalStudents(accounts.size())
                .totalExpected(totalExpected)
                .totalCollected(totalCollected)
                .totalOutstanding(totalOutstanding)
                .totalWaivers(totalWaivers)
                .fullyPaidCount((int) fullyPaid)
                .partiallyPaidCount((int) partiallyPaid)
                .unpaidCount((int) unpaid)
                .studentBreakdown(breakdown)
                .build();

    }

    @Override
    public OutstandingBalanceReport getOutstandingBalances(_User currentUser) {

        if (!RoleChecker.hasAnyRole(currentUser,
                UserRole.BURSAR, UserRole.ACCOUNTANT,
                UserRole.SUPER_ADMIN)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "unauthorized request");
        }

        List<TuitionAccount> accounts = tuitionAccountRepository
                .findAllWithOutstandingBalance();

        BigDecimal totalOutstanding = accounts.stream()
                .map(TuitionAccount::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<OutstandingBalanceEntry> entries = accounts.stream()
                .map(this::toOutstandingEntry)
                .toList();

        return OutstandingBalanceReport.builder()
                .totalStudentsWithBalance(accounts.size())
                .totalOutstandingAmount(totalOutstanding)
                .entries(entries)
                .build();
    }

    @Override
    public RegistrationSummaryReport getRegistrationSummary(_User currentUser, UUID semesterId) {

        if (!RoleChecker.hasAnyRole(currentUser,
                UserRole.REGISTRAR, UserRole.DEAN,
                UserRole.SUPER_ADMIN)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "unauthorized request");
        }

        Semester semester = findSemester(semesterId);

        List<Registration> registrations = registrationRepository
                .findBySemesterId(semesterId);

        // Count by status
        long draft = countByStatus(registrations,
                RegistrationStatus.DRAFT);
        long pending = countByStatus(registrations,
                RegistrationStatus.PENDING_DEAN);
        long approved = countByStatus(registrations,
                RegistrationStatus.APPROVED);
        long completed = countByStatus(registrations,
                RegistrationStatus.COMPLETED);

        List<RegistrationSummaryEntry> entries = registrations
                .stream()
                .map(this::toRegistrationEntry)
                .toList();

        return RegistrationSummaryReport.builder()
                .academicYear(semester.getAcademicYear())
                .semesterType(semester.getSemesterType().name())
                .totalRegistrations(registrations.size())
                .draftCount((int) draft)
                .pendingDeanCount((int) pending)
                .approvedCount((int) approved)
                .completedCount((int) completed)
                .entries(entries)
                .build();
    }

    @Override
    public WaiverReport getWaiverReport(_User currentUser) {

        if (!RoleChecker.hasAnyRole(currentUser,
                UserRole.BURSAR, UserRole.ACCOUNTANT,
                UserRole.SUPER_ADMIN)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "unauthorized request");
        }

        List<Waiver> waivers = waiverRepository
                .findAllByOrderByApprovedAtDesc();

        // Total of all reduced minimum payments granted
        // This shows how much the school relaxed
        // its payment threshold across all students
        BigDecimal totalReducedMinimumPayments = waivers.stream()
                .map(Waiver::getReducedMinimumPayment)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<WaiverReportEntry> entries = waivers.stream()
                .map(this::toWaiverEntry)
                .toList();

        return WaiverReport.builder()
                .totalWaivers(waivers.size())
                .totalReducedMinimumPayments(totalReducedMinimumPayments)
                .entries(entries)
                .build();
    }

    @Override
    public CarryoverStudentReport getCarryoverReport(_User currentUser, UUID semesterId) {

        if (!RoleChecker.hasAnyRole(currentUser,
                UserRole.REGISTRAR, UserRole.DEAN,
                UserRole.SUPER_ADMIN)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "unauthorized request");
        }

        Semester semester = findSemester(semesterId);

        // Find all registrations for this semester that have
        // at least one carryover course
        List<Registration> registrations = registrationRepository
                .findBySemesterId(semesterId);

        List<CarryoverStudentEntry> entries = registrations
                .stream()
                .map(reg -> {
                    List<RegistrationItem> carryoverItems =
                            registrationItemRepository
                                    .findByRegistrationId(reg.getId())
                                    .stream()
                                    .filter(RegistrationItem
                                            ::getIsCarryOver)
                                    .toList();

                    if (carryoverItems.isEmpty()) return null;

                    String matricNumber = studentProfileRepository
                            .findById(reg.getStudent().getId())
                            .map(StudentProfile::getMatricNumber)
                            .orElse("N/A");

                    String programName = studentProfileRepository
                            .findById(reg.getStudent().getId())
                            .map(p -> p.getProgram().getName())
                            .orElse("N/A");

                    int carryoverUnits = carryoverItems.stream()
                            .mapToInt(i -> i.getCourse()
                                    .getCreditUnits())
                            .sum();

                    List<String> courseCodes = carryoverItems
                            .stream()
                            .map(i -> i.getCourse().getCourseCode())
                            .toList();

                    return CarryoverStudentEntry.builder()
                            .studentId(reg.getStudent().getId())
                            .studentName(
                                    reg.getStudent().getFullName())
                            .matricNumber(matricNumber)
                            .programName(programName)
                            .carryoverCourseCount(
                                    carryoverItems.size())
                            .carryoverCreditUnits(carryoverUnits)
                            .carryoverCourseCodes(courseCodes)
                            .build();
                })
                .filter(entry -> entry != null)
                .toList();

        return CarryoverStudentReport.builder()
                .academicYear(semester.getAcademicYear())
                .semesterType(semester.getSemesterType().name())
                .totalStudentsWithCarryover(entries.size())
                .entries(entries)
                .build();
    }


    //=====================================
    // PRIVATE HELPER METHODS
    //=====================================


    private StudentPaymentSummary toStudentPaymentSummary(
            TuitionAccount account) {

        String matricNumber = studentProfileRepository
                .findById(account.getStudent().getId())
                .map(StudentProfile::getMatricNumber)
                .orElse("N/A");

        String programName = studentProfileRepository
                .findById(account.getStudent().getId())
                .map(p -> p.getProgram().getName())
                .orElse("N/A");

        return StudentPaymentSummary.builder()
                .studentId(account.getStudent().getId())
                .studentName(account.getStudent().getFullName())
                .matricNumber(matricNumber)
                .programName(programName)
                .totalDue(account.getTotalDue())
                .totalPaid(account.getTotalPaid())
                .balance(account.getBalance())
                .status(account.getTuitionAccountStatus())
                .build();
    }

    private OutstandingBalanceEntry toOutstandingEntry(
            TuitionAccount account) {

        String matricNumber = studentProfileRepository
                .findById(account.getStudent().getId())
                .map(StudentProfile::getMatricNumber)
                .orElse("N/A");

        String programName = studentProfileRepository
                .findById(account.getStudent().getId())
                .map(p -> p.getProgram().getName())
                .orElse("N/A");

        return OutstandingBalanceEntry.builder()
                .studentId(account.getStudent().getId())
                .studentName(account.getStudent().getFullName())
                .matricNumber(matricNumber)
                .programName(programName)
                .semesterAcademicYear(
                        account.getSemester().getAcademicYear())
                .semesterType(account.getSemester()
                        .getSemesterType().name())
                .totalDue(account.getTotalDue())
                .totalPaid(account.getTotalPaid())
                .balance(account.getBalance())
                .isBanned(account.getStudent().getIsBanned())
                .build();
    }

    private WaiverReportEntry toWaiverEntry(Waiver waiver) {

        TuitionAccount account = waiver.getTuitionAccount();

        String matricNumber = studentProfileRepository
                .findById(account.getStudent().getId())
                .map(StudentProfile::getMatricNumber)
                .orElse("N/A");

        return WaiverReportEntry.builder()
                .waiverId(waiver.getId())
                .studentId(account.getStudent().getId())
                .studentName(account.getStudent().getFullName())
                .matricNumber(matricNumber)
                .academicYear(account.getSemester().getAcademicYear())
                .semesterType(account.getSemester()
                        .getSemesterType().name())
                .allowedPaymentPercentage(
                        waiver.getAllowedPaymentPercentage())
                .reducedMinimumPayment(
                        waiver.getReducedMinimumPayment())
                .totalDue(account.getTotalDue())
                .reason(waiver.getReason())
                .approvedByName(waiver.getApprovedBy().getFullName())
                .approvedAt(waiver.getApprovedAt())
                .build();
    }

    private RegistrationSummaryEntry toRegistrationEntry(
            Registration registration) {

        String matricNumber = studentProfileRepository
                .findById(registration.getStudent().getId())
                .map(StudentProfile::getMatricNumber)
                .orElse("N/A");

        String programName = studentProfileRepository
                .findById(registration.getStudent().getId())
                .map(p -> p.getProgram().getName())
                .orElse("N/A");

        return RegistrationSummaryEntry.builder()
                .registrationId(registration.getId())
                .studentId(registration.getStudent().getId())
                .studentName(registration.getStudent().getFullName())
                .matricNumber(matricNumber)
                .programName(programName)
                .totalCreditUnits(registration.getTotalCreditUnits())
                .status(registration.getStatus())
                .submittedAt(registration.getSubmittedAt())
                .deanReviewedAt(registration.getDeanReviewAt())
                .deanRemarks(registration.getDeanRemark())
                .build();
    }



    private long countByStatus(
            List<Registration> registrations,
            RegistrationStatus status) {
        return registrations.stream()
                .filter(r -> r.getStatus() == status)
                .count();
    }

//    private BigDecimal computeWaiverAmount(
//            WaiverType type,
//            BigDecimal value,
//            BigDecimal totalDue) {
//
//        if (type == WaiverType.PERCENTAGE) {
//            return totalDue
//                    .multiply(value)
//                    .divide(BigDecimal.valueOf(100))
//                    .setScale(2, RoundingMode.HALF_UP);
//        }
//        return value.setScale(2, RoundingMode.HALF_UP);
//    }

    private Semester findSemester(UUID semesterId) {
        return semesterRepository.findById(semesterId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "semester not found"));
    }
}
