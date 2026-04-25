package com.kad.jet.program.dto;

import com.kad.jet.program.model.ProgramLevelType;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CreateProgramRequest {

    @NotBlank(message = "program name required")
    private String name;

    private ProgramLevelType programLevelType;
}
