package com.kad.jet.program.dto;

import com.kad.jet.program.model.ProgramLevelType;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProgramResponse {

    private UUID programId;
    private String name;
    private ProgramLevelType programLevelType;
    private boolean isActive;
}
