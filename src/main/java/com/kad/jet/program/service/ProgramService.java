package com.kad.jet.program.service;

import com.kad.jet.program.dto.CreateProgramRequest;
import com.kad.jet.program.dto.ProgramPageResponse;
import com.kad.jet.program.dto.ProgramResponse;
import com.kad.jet.program.dto.UpdateProgramRequest;
import com.kad.jet.user.model._User;

import java.util.UUID;

public interface ProgramService {

    ProgramResponse createNewProgram(_User currentUser, CreateProgramRequest programRequest);

    void deleteProgram(_User currentUser, UUID programId);

    ProgramResponse getProgramById(UUID programId);

    ProgramResponse updateProgram(_User currentUser, UUID programId, UpdateProgramRequest programRequest);

    ProgramPageResponse<ProgramResponse> getAllProgram(int page, int size);
}
