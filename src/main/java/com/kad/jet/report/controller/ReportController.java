package com.kad.jet.report.controller;

import com.kad.jet.report.service.ReportService;
import com.kad.jet.user.model._User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/payments/semester/{semesterId}")
    public ResponseEntity<?> getPaymentSummary(
            @AuthenticationPrincipal _User currentUser,
            @PathVariable UUID semesterId) {

        return ResponseEntity.ok(
                reportService.getPaymentSummary(
                        currentUser, semesterId));
    }

    @GetMapping("/outstanding-balances")
    public ResponseEntity<?> getOutstandingBalances(
            @AuthenticationPrincipal _User currentUser) {

        return ResponseEntity.ok(
                reportService.getOutstandingBalances(currentUser));
    }

    @GetMapping("/registrations/semester/{semesterId}")
    public ResponseEntity<?> getRegistrationSummary(
            @AuthenticationPrincipal _User currentUser,
            @PathVariable UUID semesterId) {

        return ResponseEntity.ok(
                reportService.getRegistrationSummary(
                        currentUser, semesterId));
    }

    @GetMapping("/waivers")
    public ResponseEntity<?> getWaiverReport(
            @AuthenticationPrincipal _User currentUser) {

        return ResponseEntity.ok(
                reportService.getWaiverReport(currentUser));
    }

    @GetMapping("/carryover-students/semester/{semesterId}")
    public ResponseEntity<?> getCarryoverReport(
            @AuthenticationPrincipal _User currentUser,
            @PathVariable UUID semesterId) {

        return ResponseEntity.ok(
                reportService.getCarryoverReport(
                        currentUser, semesterId));
    }
}
