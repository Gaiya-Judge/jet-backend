package com.kad.jet.finance.controller;

import com.kad.jet.finance.dto.CreateFeeStructureRequest;
import com.kad.jet.finance.dto.FeeStructureResponse;
import com.kad.jet.finance.dto.UpdateFeeStructureRequest;
import com.kad.jet.finance.service.FeeStructureService;
import com.kad.jet.user.model._User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("api/fee-structure")
@RequiredArgsConstructor
public class FeeStructureController {

    private final FeeStructureService feeStructureService;

    @PostMapping
    public ResponseEntity<?> createFeeStructure(@AuthenticationPrincipal _User currentUser, @RequestBody @Valid CreateFeeStructureRequest structureRequest){
       FeeStructureResponse response = feeStructureService.createFeeStructure(currentUser, structureRequest);

        return ResponseEntity.status(201)
                .body(response);
    }

    @GetMapping
    public ResponseEntity<?> getAllFeeStructure(@AuthenticationPrincipal _User currentUser,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable){
        Page<FeeStructureResponse> responses = feeStructureService.getAllFeeStructureResponse(currentUser, pageable);

        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@AuthenticationPrincipal _User currentUser, @PathVariable UUID id){
        FeeStructureResponse response = feeStructureService.getFeeStructureResponse(currentUser, id);

        return ResponseEntity.ok()
                .body(response);
    }

    @GetMapping("/semester/{semesterId}")
    public ResponseEntity<?> getFeeStructureBySemester(@PathVariable UUID semesterId){
        List<FeeStructureResponse> responses = feeStructureService.getFeeStructureBySemester(semesterId);

        return ResponseEntity.ok(responses);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateFeeStructure(@AuthenticationPrincipal _User currentUser,
                                                @PathVariable UUID id,
                                                @RequestBody @Valid UpdateFeeStructureRequest structureRequest){

        FeeStructureResponse response = feeStructureService.updateFeeStructure(currentUser, id, structureRequest);

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleterFeeStructure(@AuthenticationPrincipal _User currentUser,
                                                 @PathVariable UUID id){
        feeStructureService.deleteFeeStructure(currentUser, id);

        return ResponseEntity.ok("deleted");
    }



}
