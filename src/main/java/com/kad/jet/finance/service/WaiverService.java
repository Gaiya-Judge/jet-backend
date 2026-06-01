package com.kad.jet.finance.service;

import com.kad.jet.finance.dto.CreateWaiverRequest;
import com.kad.jet.finance.dto.WaiverResponse;
import com.kad.jet.user.model._User;

import java.util.List;
import java.util.UUID;

public interface WaiverService {

    WaiverResponse applyWaiver(_User currentUser,
                               CreateWaiverRequest request);

    WaiverResponse getWaiverByTuitionAccount(_User currentUser,
                                             UUID tuitionAccountId);

    List<WaiverResponse> getAllWaivers(_User currentUser);

    void removeWaiver(_User currentUser, UUID waiverId);
}
