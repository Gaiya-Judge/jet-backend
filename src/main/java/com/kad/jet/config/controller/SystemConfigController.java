package com.kad.jet.config.controller;

import com.kad.jet.config.dto.SystemConfigResponse;
import com.kad.jet.config.dto.UpdateSystemConfigRequest;
import com.kad.jet.config.service.SystemConfigApiService;
import com.kad.jet.user.model._User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/config")
@RequiredArgsConstructor
public class SystemConfigController {

    private final SystemConfigApiService systemConfigApiService;

    @GetMapping
    public ResponseEntity<?> getAllConfigs(
            @AuthenticationPrincipal _User currentUser) {

        List<SystemConfigResponse> response =
                systemConfigApiService.getAllConfigs(currentUser);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{key}")
    public ResponseEntity<?> getConfigByKey(
            @AuthenticationPrincipal _User currentUser,
            @PathVariable String key) {

        SystemConfigResponse response =
                systemConfigApiService
                        .getConfigByKey(currentUser, key);

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{key}")
    public ResponseEntity<?> updateConfig(
            @AuthenticationPrincipal _User currentUser,
            @PathVariable String key,
            @Valid @RequestBody UpdateSystemConfigRequest request) {

        SystemConfigResponse response =
                systemConfigApiService
                        .updateConfig(currentUser, key, request);

        return ResponseEntity.ok(response);
    }
}
