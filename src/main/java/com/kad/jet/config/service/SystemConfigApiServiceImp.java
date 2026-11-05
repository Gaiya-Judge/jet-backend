package com.kad.jet.config.service;

import com.kad.jet.config.SystemConfigKeys;
import com.kad.jet.config.dto.SystemConfigResponse;
import com.kad.jet.config.dto.UpdateSystemConfigRequest;
import com.kad.jet.config.mapper.SystemConfigMapper;
import com.kad.jet.config.model.SystemConfig;
import com.kad.jet.config.repository.SystemConfigRepository;
import com.kad.jet.user.model.UserRole;
import com.kad.jet.user.model._User;
import com.kad.jet.user.utility.RoleChecker;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemConfigApiServiceImp implements SystemConfigApiService {

    private final SystemConfigRepository systemConfigRepository;

    @Override
    public List<SystemConfigResponse> getAllConfigs(_User currentUser) {

        if (!RoleChecker.hasAnyRole(currentUser,
                UserRole.SUPER_ADMIN)) {
            throw new ResponseStatusException(
                    currentUser == null ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN, "unauthorized request");
        }

        return systemConfigRepository.findAll()
                .stream()
                .map(SystemConfigMapper::toDto)
                .toList();
    }

    @Override
    public SystemConfigResponse getConfigByKey(_User currentUser, String key) {

        if (!RoleChecker.hasAnyRole(currentUser,
                UserRole.SUPER_ADMIN)) {
            throw new ResponseStatusException(
                    currentUser == null ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN, "unauthorized request");
        }

        return SystemConfigMapper.toDto(findByKey(key));
    }

    @Override
    @Transactional
    public SystemConfigResponse updateConfig(_User currentUser, String key, UpdateSystemConfigRequest request) {

        if (!RoleChecker.hasAnyRole(currentUser,
                UserRole.SUPER_ADMIN)) {
            throw new ResponseStatusException(
                    currentUser == null ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN, "unauthorized request");
        }

        SystemConfig config = findByKey(key);

        // Validate the new value makes sense
        // for numeric config keys
        validateConfigValue(key, request.getConfigValue());

        String oldValue = config.getConfigValue();
        config.setConfigValue(request.getConfigValue());
        config.setUpdatedBy(currentUser);

        systemConfigRepository.save(config);

        log.info("System config '{}' updated by SUPER_ADMIN {}. " +
                        "Old value: {} New value: {}",
                key,
                currentUser.getId(),
                oldValue,
                request.getConfigValue());

        return SystemConfigMapper.toDto(config);
    }


    private SystemConfig findByKey(String key) {
        return systemConfigRepository.findByConfigKey(key)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "config key '" + key + "' not found"));
    }

    /**
     * Validates that config values make business sense.
     * Prevents SUPER_ADMIN from accidentally setting
     * MAX_CREDIT_UNITS to 0 or MIN_PAYMENT_PERCENTAGE to 150.
     */
    private void validateConfigValue(String key, String value) {

        try {
            int intValue = Integer.parseInt(value);

            switch (key) {
                case SystemConfigKeys.MAX_CREDIT_UNITS:
                    if (intValue < 1 || intValue > 60) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                "MAX_CREDIT_UNITS must be " +
                                        "between 1 and 60");
                    }
                    break;

                case SystemConfigKeys.MIN_CREDIT_UNITS:
                    if (intValue < 1 || intValue > 30) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                "MIN_CREDIT_UNITS must be " +
                                        "between 1 and 30");
                    }
                    break;

                case SystemConfigKeys.MIN_PAYMENT_PERCENTAGE:
                    if (intValue < 1 || intValue > 100) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                "MIN_PAYMENT_PERCENTAGE must be " +
                                        "between 1 and 100");
                    }
                    break;
            }

        } catch (NumberFormatException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "value '" + value + "' is not a valid number " +
                            "for config key '" + key + "'");
        }
    }
}
