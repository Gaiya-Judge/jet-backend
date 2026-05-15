package com.kad.jet.config.service;

import com.kad.jet.config.model.SystemConfig;
import com.kad.jet.config.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemConfigService {

    private final SystemConfigRepository systemConfigRepository;

    public String getValue(String key) {
        return systemConfigRepository.findByConfigKey(key)
                .map(SystemConfig::getConfigValue)
                .orElseThrow(() -> {
                    log.error("System config key not found: {}",
                            key);
                    return new ResponseStatusException(
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            "system configuration missing for key: "
                                    + key +
                                    ". please contact IT admin.");
                });
    }

    public int getIntValue(String key) {
        String value = getValue(key);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.error("System config key {} has non-integer " +
                    "value: {}", key, value);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "system configuration value for " + key +
                            " is not a valid number. " +
                            "please contact IT admin.");
        }
    }
}
