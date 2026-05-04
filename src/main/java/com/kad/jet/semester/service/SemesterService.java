package com.kad.jet.semester.service;

import com.kad.jet.semester.dto.*;
import com.kad.jet.user.model._User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface SemesterService {

    SemesterResponse createSemester(_User currentUser, CreateSemesterRequest semesterRequest);

    SemesterResponse getSemesterById(UUID semesterId);

    SemesterResponse getActiveSemester();

    Page<SemesterResponse> getAllSemester(Pageable pageable);

    SemesterResponse updateSemester(_User currentUser, UUID semesterId, UpdateSemesterRequest updateSemester);

    SemesterResponse updateStatus(_User currentUser, UUID semesterId, SemesterStatusRequest semesterStatusRequest);

    SemesterResponse configureAddAndDrop(_User currentUser, UUID semesterId, AddDropRequest addDropRequest);
}
