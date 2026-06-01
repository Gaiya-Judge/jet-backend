package com.kad.jet.finance.controller;

import com.kad.jet.finance.dto.CreateWaiverRequest;
import com.kad.jet.finance.dto.WaiverResponse;
import com.kad.jet.finance.service.WaiverService;
import com.kad.jet.user.model._User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("api/waiver")
@RequiredArgsConstructor
public class WaiverController {

    private final WaiverService waiverService;

    @PostMapping
    public ResponseEntity<?> applyWaiver(
            @AuthenticationPrincipal _User currentUser,
            @Valid @RequestBody CreateWaiverRequest request) {

        WaiverResponse response =
                waiverService.applyWaiver(currentUser, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/tuition-account/{tuitionAccountId}")
    public ResponseEntity<?> getWaiverByTuitionAccount(
            @AuthenticationPrincipal _User currentUser,
            @PathVariable UUID tuitionAccountId) {

        WaiverResponse response =
                waiverService.getWaiverByTuitionAccount(
                        currentUser, tuitionAccountId);

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<?> getAllWaivers(
            @AuthenticationPrincipal _User currentUser) {

        List<WaiverResponse> response =
                waiverService.getAllWaivers(currentUser);

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> removeWaiver(
            @AuthenticationPrincipal _User currentUser,
            @PathVariable UUID id) {

        waiverService.removeWaiver(currentUser, id);

        return ResponseEntity.noContent().build();
    }
}
