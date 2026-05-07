package com.kad.jet.finance.service;

import com.kad.jet.finance.dto.CreateFeeStructureRequest;
import com.kad.jet.finance.dto.FeeStructureResponse;
import com.kad.jet.finance.dto.UpdateFeeStructureRequest;
import com.kad.jet.user.model._User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface FeeStructureService {

    FeeStructureResponse createFeeStructure(_User currentUser, CreateFeeStructureRequest structureRequest);

    FeeStructureResponse getFeeStructureResponse(_User currentUser, UUID id);

    Page<FeeStructureResponse> getAllFeeStructureResponse(_User currentUser, Pageable pageable);

    List<FeeStructureResponse> getFeeStructureBySemester(UUID semesterId);

    FeeStructureResponse updateFeeStructure(_User currentUser, UUID feeStructureId, UpdateFeeStructureRequest structureRequest);

    void deleteFeeStructure(_User currentUser, UUID feeStructureId);
}
