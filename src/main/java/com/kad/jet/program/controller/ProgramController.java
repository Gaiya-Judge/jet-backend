package com.kad.jet.program.controller;

import com.kad.jet.program.dto.CreateProgramRequest;
import com.kad.jet.program.dto.ProgramPageResponse;
import com.kad.jet.program.dto.ProgramResponse;
import com.kad.jet.program.dto.UpdateProgramRequest;
import com.kad.jet.program.service.ProgramService;
import com.kad.jet.user.model._User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("api/program")
@RequiredArgsConstructor
public class ProgramController {

    private final ProgramService programService;

    @PostMapping("/create")
    public ResponseEntity<?> createProgram(@AuthenticationPrincipal _User currentUser, @RequestBody @Valid CreateProgramRequest programRequest){
        ProgramResponse response = programService.createNewProgram(currentUser, programRequest);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteProgram(@AuthenticationPrincipal _User currentUser, @PathVariable(name = "id")UUID programId){
        programService.deleteProgram(currentUser, programId);

        return ResponseEntity.ok("program deleted");
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getProgramById(@PathVariable(name = "id") UUID programId){
        ProgramResponse response = programService.getProgramById(programId);

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateProgram(@AuthenticationPrincipal _User currentUser, @PathVariable(name = "id") UUID programId, @RequestBody UpdateProgramRequest programRequest){
        ProgramResponse response = programService.updateProgram(currentUser, programId, programRequest);

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<?> getAllProgram(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size){
        ProgramPageResponse<ProgramResponse> response = programService.getAllProgram(page, size);

        return ResponseEntity.ok(response);
    }
}
