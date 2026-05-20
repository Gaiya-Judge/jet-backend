package com.kad.jet.config.service;

import com.kad.jet.config.dto.SystemConfigResponse;
import com.kad.jet.config.dto.UpdateSystemConfigRequest;
import com.kad.jet.user.model._User;

import java.util.List;

public interface SystemConfigApiService {

    List<SystemConfigResponse> getAllConfigs(_User currentUser);

    SystemConfigResponse getConfigByKey(
            _User currentUser, String key);

    SystemConfigResponse updateConfig(
            _User currentUser,
            String key,
            UpdateSystemConfigRequest request);
}
