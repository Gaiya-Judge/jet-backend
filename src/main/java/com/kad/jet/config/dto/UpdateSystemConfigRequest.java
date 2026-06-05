package com.kad.jet.config.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateSystemConfigRequest {

    @NotBlank(message = "value is required")
    private String configValue;
}
