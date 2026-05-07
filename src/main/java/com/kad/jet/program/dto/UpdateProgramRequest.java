package com.kad.jet.program.dto;

import com.kad.jet.program.model.ProgramLevelType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UpdateProgramRequest {


    private String name;

    private ProgramLevelType programLevelType;
}
