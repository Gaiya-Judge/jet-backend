package com.kad.jet.program.mapper;

import com.kad.jet.program.dto.ProgramResponse;
import com.kad.jet.program.model.Program;

public class ProgramMapper {

    public static ProgramResponse toDto(Program program){
        return ProgramResponse
                .builder()
                .name(program.getName())
                .programId(program.getId())
                .programLevelType(program.getLevelType())
                .build();
    }
}
