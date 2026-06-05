package com.kad.jet.config.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SystemConfigResponse {

    private String configKey;
    private String configValue;
    private String description;
    private String updatedByName;
    private LocalDateTime updatedAt;
}
