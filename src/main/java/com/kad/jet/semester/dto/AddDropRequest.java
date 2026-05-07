package com.kad.jet.semester.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class AddDropRequest {

    @NotNull(message = "add drop start date is required")
    private LocalDate addDropStart;

    @NotNull(message = "add drop end date is required")
    private LocalDate addDropEnd;
}
