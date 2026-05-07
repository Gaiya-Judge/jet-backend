package com.kad.jet.finance.controller;

import com.kad.jet.finance.dto.AdminChargeResponse;
import com.kad.jet.finance.dto.CreateAdminChargeRequest;
import com.kad.jet.finance.dto.UpdateAdminChargeRequest;
import com.kad.jet.finance.service.AdminChargeService;
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
@RequestMapping("api/admin-charges")
@RequiredArgsConstructor
public class AdminChargeController {

    private final AdminChargeService adminChargeService;

    @PostMapping
    public ResponseEntity<?> createAdminCharge(@AuthenticationPrincipal _User currentUser,
                                               @RequestBody @Valid CreateAdminChargeRequest chargeRequest){
        AdminChargeResponse response = adminChargeService.createAdminCharge(currentUser, chargeRequest);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable UUID id){
        AdminChargeResponse response = adminChargeService.getAdminChargeById(id);

        return ResponseEntity.ok()
                .body(response);
    }

    @GetMapping("/semester/{semesterId}")
    public ResponseEntity<?> getBySemesterId(@PathVariable UUID semesterId){
        List<AdminChargeResponse> responses = adminChargeService.getAdminChargesBySemester(semesterId);

        return ResponseEntity.ok(responses);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateAdminCharges(@PathVariable UUID id,
                                                @AuthenticationPrincipal _User currentUser,
                                                @RequestBody @Valid UpdateAdminChargeRequest chargeRequest){
        AdminChargeResponse response = adminChargeService.updateAdminCharge(currentUser, id, chargeRequest);

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteAdminCharge(@PathVariable UUID id,
                                               @AuthenticationPrincipal _User currentUser){
        adminChargeService.deleteAdminCharge(currentUser, id);

        return ResponseEntity.noContent().build();
    }
}
