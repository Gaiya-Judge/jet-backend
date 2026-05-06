package com.kad.jet.registration.service;

import com.kad.jet.registration.dto.*;
import com.kad.jet.user.model._User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface RegistrationService {

    RegistrationResponse createDraft(_User currentUser, CreateRegistrationRequest request);

    RegistrationResponse getRegistrationById(_User currentUser, UUID id);

    Page<RegistrationResponse> getAllRegistrations(_User currentUser, Pageable pageable);

    Page<RegistrationResponse> getPendingRegistrations(_User currentUser, Pageable pageable);

    List<RegistrationResponse> getMyRegistrations(_User currentUser);

    RegistrationResponse addCourse(_User currentUser, UUID registrationId, AddCourseRequest request);

    RegistrationResponse removeCourse(_User currentUser, UUID registrationId, UUID courseId);

    List<RegistrationItemResponse> getRegistrationCourses(_User currentUser, UUID registrationId);

    RegistrationResponse submitRegistration(_User currentUser, UUID registrationId, SubmitRegistrationRequest request);

    RegistrationResponse approveRegistration(_User currentUser, UUID registrationId, DeanReviewRequest request);

    RegistrationResponse rejectRegistration(_User currentUser, UUID registrationId, DeanReviewRequest request);

    TuitionEstimateResponse getTuitionEstimate(_User currentUser, UUID registrationId);
}
