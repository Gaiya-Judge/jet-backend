package com.kad.jet.finance.controller;

import com.kad.jet.finance.dto.*;
import com.kad.jet.finance.service.PaymentService;
import com.kad.jet.user.model._User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("api/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/initiate")
    public ResponseEntity<?> initiatePayment(
            @AuthenticationPrincipal _User currentUser,
            @Valid @RequestBody InitiatePaymentRequest request) {

        InitiatePaymentResponse response =
                paymentService.initiatePayment(currentUser, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(response);
    }

    @PostMapping("/webhook")
    public ResponseEntity<?> handleWebhook(
            @RequestHeader("x-paystack-signature")
            String paystackSignature,
            @RequestBody String rawPayload) {

        paymentService.handleWebhook(paystackSignature, rawPayload);
        return ResponseEntity.ok().build();
    }


    @PostMapping("/bank-transfer")
    public ResponseEntity<?> submitBankTransfer(
            @AuthenticationPrincipal _User currentUser,
            @Valid @RequestBody BankTransferRequest request) {

        PaymentResponse response =
                paymentService.submitBankTransfer(
                        currentUser, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(response);
    }

    @PutMapping("/{id}/verify")
    public ResponseEntity<?> verifyBankTransfer(
            @AuthenticationPrincipal _User currentUser,
            @PathVariable UUID id,
            @RequestBody VerifyPaymentRequest request) {

        PaymentResponse response =
                paymentService.verifyBankTransfer(
                        currentUser, id, request);

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/reject")
    public ResponseEntity<?> rejectBankTransfer(
            @AuthenticationPrincipal _User currentUser,
            @PathVariable UUID id,
            @Valid @RequestBody RejectPaymentRequest request) {

        PaymentResponse response =
                paymentService.rejectBankTransfer(
                        currentUser, id, request);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/tuition-account/{tuitionAccountId}")
    public ResponseEntity<?> getTuitionAccount(
            @AuthenticationPrincipal _User currentUser,
            @PathVariable UUID tuitionAccountId) {

        TuitionAccountResponse response =
                paymentService.getTuitionAccount(
                        currentUser, tuitionAccountId);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/pending-verification")
    public ResponseEntity<?> getPendingBankTransfers(
            @AuthenticationPrincipal _User currentUser,
            @PageableDefault(size = 10, sort = "createdAt",
                    direction = Sort.Direction.ASC)
            Pageable pageable) {

        Page<PaymentResponse> response =
                paymentService.getPendingBankTransfers(
                        currentUser, pageable);

        return ResponseEntity.ok(response);
    }
}
