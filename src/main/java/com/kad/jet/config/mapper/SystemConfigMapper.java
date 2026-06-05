package com.kad.jet.config.mapper;

import com.kad.jet.config.dto.SystemConfigResponse;
import com.kad.jet.config.model.SystemConfig;

public class SystemConfigMapper {

    public static SystemConfigResponse toDto(SystemConfig config) {

        return SystemConfigResponse.builder()
                .configKey(config.getConfigKey())
                .configValue(config.getConfigValue())
                .description(config.getDescription())
                .updatedByName(config.getUpdatedBy() != null
                        ? config.getUpdatedBy().getFullName()
                        : "system")
                .updatedAt(config.getUpdatedAt())
                .build();
    }
}
