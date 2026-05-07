package com.kad.jet.finance.service;

import com.kad.jet.finance.dto.AdminChargeResponse;
import com.kad.jet.finance.dto.CreateAdminChargeRequest;
import com.kad.jet.finance.dto.UpdateAdminChargeRequest;
import com.kad.jet.user.model._User;

import java.util.List;
import java.util.UUID;

public interface AdminChargeService {

    AdminChargeResponse createAdminCharge(_User currentUser, CreateAdminChargeRequest request);

    AdminChargeResponse getAdminChargeById(UUID id);

    List<AdminChargeResponse> getAdminChargesBySemester(UUID semesterId);

    AdminChargeResponse updateAdminCharge(_User currentUser, UUID id, UpdateAdminChargeRequest request);

    void deleteAdminCharge(_User currentUser, UUID id);
}
