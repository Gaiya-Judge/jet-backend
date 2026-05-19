package com.kad.jet.finance.service;

import com.kad.jet.finance.dto.CreateWaiverRequest;
import com.kad.jet.finance.dto.WaiverResponse;
import com.kad.jet.finance.mapper.WaiverMapper;
import com.kad.jet.finance.model.TuitionAccount;
import com.kad.jet.finance.model.TuitionAccountStatus;
import com.kad.jet.finance.model.Waiver;
import com.kad.jet.finance.model.WaiverType;
import com.kad.jet.finance.repository.TuitionAccountRepository;
import com.kad.jet.finance.repository.WaiverRepository;
import com.kad.jet.user.model.UserRole;
import com.kad.jet.user.model._User;
import com.kad.jet.user.utility.RoleChecker;
import jakarta.transaction.Transactional;
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
public class WaiverServiceImp implements WaiverService {

    private final WaiverRepository waiverRepository;
    private final TuitionAccountRepository tuitionAccountRepository;

    @Override
    @Transactional
    public WaiverResponse applyWaiver(_User currentUser, CreateWaiverRequest request) {

        if (!RoleChecker.is_bursary(currentUser)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "unauthorized request");
        }

        TuitionAccount account = findTuitionAccount(
                request.getTuitionAccountId());

        // Cannot waive a fully paid account
        if (account.getTuitionAccountStatus() == TuitionAccountStatus.PAID) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "cannot apply waiver to a fully paid " +
                            "tuition account");
        }

        // Validate percentage waiver does not exceed 100%
        if (request.getWaiverType() == WaiverType.PERCENTAGE &&
                request.getValue().compareTo(
                        BigDecimal.valueOf(100)) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "percentage waiver cannot exceed 100%");
        }

        // Calculate the actual Naira amount this waiver deducts
        BigDecimal computedAmount = computeWaiverAmount(
                request.getWaiverType(),
                request.getValue(),
                account.getTotalDue());

        // Waiver cannot exceed the current balance
        // Cannot give more discount than what is owed
        if (computedAmount.compareTo(account.getBalance()) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "waiver amount ₦" + computedAmount +
                            " exceeds current balance ₦" +
                            account.getBalance());
        }

        // Save the waiver record
        Waiver waiver = Waiver.builder()
                .tuitionAccount(account)
                .waiverType(request.getWaiverType())
                .value(request.getValue())
                .reason(request.getReason())
                .approvedBy(currentUser)
                .build();

        waiverRepository.save(waiver);

        // Recalculate tuition account figures
        // waiver_amount accumulates — a student can have
        // multiple waivers on the same account
        BigDecimal newWaiverTotal = account.getWaiverAmount()
                .add(computedAmount);
        account.setWaiverAmount(newWaiverTotal);

        // Recalculate total due
        // total_due = tuition + admin_charges
        //             - waiver_amount - credit_carried_forward
        BigDecimal newTotalDue = account.getTuitionAmount()
                .add(account.getAdminChargesTotal())
                .subtract(newWaiverTotal)
                .subtract(account.getCreditCarriedForward());

        // Total due cannot go below zero
        if (newTotalDue.compareTo(BigDecimal.ZERO) < 0) {
            newTotalDue = BigDecimal.ZERO;
        }

        account.setTotalDue(newTotalDue);

        // Recalculate minimum payment based on new total due
        // Read percentage from system config
        BigDecimal newMinimumPayment = newTotalDue
                .multiply(BigDecimal.valueOf(70))
                .divide(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.CEILING);
        account.setMinimumPayment(newMinimumPayment);

        // Recalculate balance
        BigDecimal newBalance = newTotalDue
                .subtract(account.getTotalPaid());
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            newBalance = BigDecimal.ZERO;
        }
        account.setBalance(newBalance);

        // Update status to ADJUSTED — signals a Bursar
        // manually changed the financial figures
        account.setTuitionAccountStatus(TuitionAccountStatus.ADJUSTED);

        tuitionAccountRepository.save(account);

        log.info("Waiver applied by Bursar {}. " +
                        "Account: {} Type: {} Value: {} Amount: ₦{}",
                currentUser.getId(),
                account.getId(),
                request.getWaiverType(),
                request.getValue(),
                computedAmount);

        return WaiverMapper.toDto(waiver, computedAmount);
    }

    @Override
    public WaiverResponse getWaiverById(_User currentUser, UUID id) {
        if (!RoleChecker.hasAnyRole(currentUser,
                UserRole.BURSAR, UserRole.SUPER_ADMIN)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "unauthorized request");
        }

        Waiver waiver = findById(id);
        BigDecimal computedAmount = computeWaiverAmount(
                waiver.getWaiverType(),
                waiver.getValue(),
                waiver.getTuitionAccount().getTotalDue());

        return WaiverMapper.toDto(waiver, computedAmount);
    }

    @Override
    public List<WaiverResponse> getWaiversByTuitionAccount(_User currentUser, UUID tuitionAccountId) {
        if (!RoleChecker.hasAnyRole(currentUser,
                UserRole.BURSAR, UserRole.STUDENT,
                UserRole.SUPER_ADMIN)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "unauthorized request");
        }

        // Validate account exists
        TuitionAccount account = findTuitionAccount(
                tuitionAccountId);

        // Student can only see their own account waivers
        if (RoleChecker.is_student(currentUser) &&
                !account.getStudent().getId()
                        .equals(currentUser.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "you can only view your own tuition account");
        }

        return waiverRepository
                .findByTuitionAccountId(tuitionAccountId)
                .stream()
                .map(w -> WaiverMapper.toDto(w,
                        computeWaiverAmount(
                                w.getWaiverType(),
                                w.getValue(),
                                account.getTotalDue())))
                .toList();
    }

    @Override
    @Transactional
    public void removeWaiver(_User currentUser, UUID id) {

        if (!RoleChecker.hasAnyRole(currentUser,
                UserRole.SUPER_ADMIN)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "only SUPER_ADMIN can remove a waiver");
        }

        Waiver waiver = findById(id);
        TuitionAccount account = waiver.getTuitionAccount();

        // Cannot remove waiver from a fully paid account
        // The payment has already been made with the discount
        // Removing it now would create a phantom debt
        if (account.getTuitionAccountStatus() == TuitionAccountStatus.PAID) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "cannot remove a waiver from a fully paid " +
                            "tuition account");
        }

        // Calculate how much this waiver contributed
        BigDecimal computedAmount = computeWaiverAmount(
                waiver.getWaiverType(),
                waiver.getValue(),
                account.getTotalDue());

        // Delete the waiver
        waiverRepository.delete(waiver);

        // Reverse the waiver from the account figures
        BigDecimal newWaiverTotal = account.getWaiverAmount()
                .subtract(computedAmount);
        if (newWaiverTotal.compareTo(BigDecimal.ZERO) < 0) {
            newWaiverTotal = BigDecimal.ZERO;
        }
        account.setWaiverAmount(newWaiverTotal);

        // Recalculate total due going back up
        BigDecimal newTotalDue = account.getTuitionAmount()
                .add(account.getAdminChargesTotal())
                .subtract(newWaiverTotal)
                .subtract(account.getCreditCarriedForward());

        account.setTotalDue(newTotalDue);

        // Recalculate minimum payment
        BigDecimal newMinimumPayment = newTotalDue
                .multiply(BigDecimal.valueOf(70))
                .divide(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.CEILING);
        account.setMinimumPayment(newMinimumPayment);

        // Recalculate balance
        BigDecimal newBalance = newTotalDue
                .subtract(account.getTotalPaid());
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            newBalance = BigDecimal.ZERO;
        }
        account.setBalance(newBalance);

        tuitionAccountRepository.save(account);

        log.info("Waiver {} removed by SUPER_ADMIN {}. " +
                        "Account {} recalculated. New total due: ₦{}",
                id,
                currentUser.getId(),
                account.getId(),
                newTotalDue);
    }

    private BigDecimal computeWaiverAmount(
            WaiverType type,
            BigDecimal value,
            BigDecimal totalDue) {

        if (type == WaiverType.PERCENTAGE) {
            return totalDue
                    .multiply(value)
                    .divide(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        // FIXED — value IS the amount
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private Waiver findById(UUID id) {
        return waiverRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "waiver not found"));
    }

    private TuitionAccount findTuitionAccount(UUID id) {
        return tuitionAccountRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "tuition account not found"));
    }
}
