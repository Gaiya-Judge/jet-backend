package com.kad.jet.finance.service;

import com.kad.jet.config.SystemConfigKeys;
import com.kad.jet.config.service.SystemConfigService;
import com.kad.jet.finance.dto.CreateWaiverRequest;
import com.kad.jet.finance.dto.WaiverResponse;
import com.kad.jet.finance.model.TuitionAccount;
import com.kad.jet.finance.model.TuitionAccountStatus;
import com.kad.jet.finance.model.Waiver;
import com.kad.jet.finance.repository.TuitionAccountRepository;
import com.kad.jet.finance.repository.WaiverRepository;
import com.kad.jet.notification.event.WaiverAppliedEvent;
import com.kad.jet.student.model.StudentProfile;
import com.kad.jet.student.repository.StudentProfileRepository;
import com.kad.jet.user.model.UserRole;
import com.kad.jet.user.model._User;
import com.kad.jet.user.utility.RoleChecker;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
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
public class WaiverServiceImp implements WaiverService {

    private final WaiverRepository waiverRepository;
    private final TuitionAccountRepository tuitionAccountRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final SystemConfigService systemConfigService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public WaiverResponse applyWaiver(_User currentUser, CreateWaiverRequest request) {

        if (!RoleChecker.is_bursary(currentUser)) {
            throw new ResponseStatusException(
                    currentUser == null ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN, "unauthorized request");
        }

        TuitionAccount account = findTuitionAccount(
                request.getTuitionAccountId());

        // Cannot apply waiver to a fully paid account
        // No point reducing minimum payment if already paid
        if (account.getTuitionAccountStatus() == TuitionAccountStatus.PAID) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "cannot apply waiver to a fully paid " +
                            "tuition account");
        }

        // Cannot apply if student has already paid enough
        // to complete registration
        if (account.getTuitionAccountStatus() == TuitionAccountStatus.PARTIAL ||
                account.getTuitionAccountStatus() ==
                        TuitionAccountStatus.COMPLETED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "student has already met the payment " +
                            "threshold. waiver is not needed.");
        }

        // One waiver per tuition account
        if (waiverRepository.existsByTuitionAccountId(
                account.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "a waiver already exists for this tuition " +
                            "account. remove the existing waiver before " +
                            "applying a new one.");
        }

        // Waiver percentage must be LESS than the system default
        // It makes no sense to grant a waiver that is higher
        // than or equal to what is already required
        int systemMinPercentage = systemConfigService.getIntValue(
                SystemConfigKeys.MIN_PAYMENT_PERCENTAGE);

        if (request.getAllowedPaymentPercentage()
                >= systemMinPercentage) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "waiver percentage (" +
                            request.getAllowedPaymentPercentage() +
                            "%) must be less than the system minimum " +
                            "payment percentage (" +
                            systemMinPercentage + "%). " +
                            "a waiver that is equal to or higher than " +
                            "the standard threshold has no effect.");
        }

        // Calculate the reduced minimum payment
        // This is the only thing that changes
        BigDecimal reducedMinimumPayment = account.getTotalDue()
                .multiply(BigDecimal.valueOf(
                        request.getAllowedPaymentPercentage()))
                .divide(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.CEILING);

        // Save the waiver
        Waiver waiver = Waiver.builder()
                .tuitionAccount(account)
                .allowedPaymentPercentage(
                        request.getAllowedPaymentPercentage())
                .reducedMinimumPayment(reducedMinimumPayment)
                .reason(request.getReason())
                .approvedBy(currentUser)
                .build();

        waiverRepository.save(waiver);

        // Update the minimum payment on the tuition account
        // This is the ONLY field that changes
        // totalDue, balance, tuitionAmount — all unchanged
        BigDecimal originalMinimumPayment =
                account.getMinimumPayment();

        account.setMinimumPayment(reducedMinimumPayment);
        tuitionAccountRepository.save(account);

        log.info("Waiver applied by Bursar {}. " +
                        "Account: {} Student still owes: ₦{} " +
                        "New minimum payment: ₦{} ({}%) " +
                        "Previously: ₦{} ({}%)",
                currentUser.getId(),
                account.getId(),
                account.getTotalDue(),
                reducedMinimumPayment,
                request.getAllowedPaymentPercentage(),
                originalMinimumPayment,
                systemMinPercentage);

        return buildResponse(
                waiver, account, originalMinimumPayment);
    }

    @Override
    public WaiverResponse getWaiverByTuitionAccount(_User currentUser, UUID tuitionAccountId) {

        if (!RoleChecker.hasAnyRole(currentUser,
                UserRole.BURSAR, UserRole.STUDENT,
                UserRole.SUPER_ADMIN)) {
            throw new ResponseStatusException(
                    currentUser == null ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN, "unauthorized request");
        }

        TuitionAccount account = findTuitionAccount(
                tuitionAccountId);

        // Student can only see their own account waiver
        if (RoleChecker.is_student(currentUser) &&
                !account.getStudent().getId()
                        .equals(currentUser.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "you can only view your own tuition account");
        }

        Waiver waiver = waiverRepository
                .findByTuitionAccountId(tuitionAccountId)
                .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "no waiver found for this tuition account"));

        int systemMinPercentage = systemConfigService.getIntValue(
                SystemConfigKeys.MIN_PAYMENT_PERCENTAGE);

        BigDecimal originalMinimumPayment = account.getTotalDue()
                .multiply(BigDecimal.valueOf(systemMinPercentage))
                .divide(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.CEILING);

        eventPublisher.publishEvent(
                new WaiverAppliedEvent(
                        waiver.getId(),
                        waiver.getTuitionAccount().getId(),
                        waiver.getTuitionAccount().getStudent().getId(),
                        currentUser.getId()));

        return buildResponse(
                waiver, account, originalMinimumPayment);
    }

    @Override
    public List<WaiverResponse> getAllWaivers(_User currentUser) {


        if (!RoleChecker.hasAnyRole(currentUser,
                UserRole.BURSAR, UserRole.SUPER_ADMIN)) {
            throw new ResponseStatusException(
                    currentUser == null ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN, "unauthorized request");
        }

        int systemMinPercentage = systemConfigService.getIntValue(
                SystemConfigKeys.MIN_PAYMENT_PERCENTAGE);

        return waiverRepository.findAllByOrderByApprovedAtDesc()
                .stream()
                .map(waiver -> {
                    TuitionAccount account =
                            waiver.getTuitionAccount();

                    BigDecimal originalMinimum =
                            account.getTotalDue()
                                    .multiply(BigDecimal.valueOf(
                                            systemMinPercentage))
                                    .divide(BigDecimal.valueOf(100))
                                    .setScale(2,
                                            RoundingMode.CEILING);

                    return buildResponse(
                            waiver, account, originalMinimum);
                })
                .toList();
    }

    @Override
    public void removeWaiver(_User currentUser, UUID waiverId) {


        if (!RoleChecker.hasAnyRole(currentUser,
                UserRole.SUPER_ADMIN)) {
            throw new ResponseStatusException(
                    currentUser == null ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN,
                    "only SUPER_ADMIN can remove a waiver");
        }

        Waiver waiver = waiverRepository.findById(waiverId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "waiver not found"));

        TuitionAccount account = waiver.getTuitionAccount();

        // Cannot remove waiver if student has already paid
        // based on the reduced threshold
        // Removing it now would make a paid registration
        // suddenly appear underpaid
        if (account.getTuitionAccountStatus() == TuitionAccountStatus.PAID ||
                account.getTuitionAccountStatus() ==
                        TuitionAccountStatus.PARTIAL) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "cannot remove waiver. student has already " +
                            "made payment based on the waiver terms. " +
                            "contact IT admin if this must be reversed.");
        }

        // Restore the original minimum payment
        int systemMinPercentage = systemConfigService.getIntValue(
                SystemConfigKeys.MIN_PAYMENT_PERCENTAGE);

        BigDecimal originalMinimumPayment = account.getTotalDue()
                .multiply(BigDecimal.valueOf(systemMinPercentage))
                .divide(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.CEILING);

        account.setMinimumPayment(originalMinimumPayment);
        tuitionAccountRepository.save(account);

        waiverRepository.delete(waiver);

        log.info("Waiver removed by SUPER_ADMIN {}. " +
                        "Account {} minimum payment restored to ₦{} ({}%)",
                currentUser.getId(),
                account.getId(),
                originalMinimumPayment,
                systemMinPercentage);
    }

    private WaiverResponse buildResponse(
            Waiver waiver,
            TuitionAccount account,
            BigDecimal originalMinimumPayment) {

        String matricNumber = studentProfileRepository
                .findById(account.getStudent().getId())
                .map(StudentProfile::getMatricNumber)
                .orElse("N/A");

        return WaiverResponse.builder()
                .id(waiver.getId())
                .tuitionAccountId(account.getId())
                .studentName(account.getStudent().getFullName())
                .matricNumber(matricNumber)
                .allowedPaymentPercentage(
                        waiver.getAllowedPaymentPercentage())
                .reducedMinimumPayment(
                        waiver.getReducedMinimumPayment())
                .originalMinimumPayment(originalMinimumPayment)
                .totalDue(account.getTotalDue())
                // Always show full amount — unchanged by waiver
                .reason(waiver.getReason())
                .approvedByName(
                        waiver.getApprovedBy().getFullName())
                .approvedAt(waiver.getApprovedAt())
                .build();
    }

    private TuitionAccount findTuitionAccount(UUID id) {
        return tuitionAccountRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "tuition account not found"));
    }
}
