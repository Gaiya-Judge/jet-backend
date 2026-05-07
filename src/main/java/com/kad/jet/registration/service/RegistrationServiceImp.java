package com.kad.jet.registration.service;

import com.kad.jet.course.repository.CourseRepository;
import com.kad.jet.finance.repository.AdminChargeRepository;
import com.kad.jet.finance.repository.FeeStructureRepository;
import com.kad.jet.finance.repository.StudentGradeRepository;
import com.kad.jet.registration.dto.*;
import com.kad.jet.registration.repository.RegistrationItemRepository;
import com.kad.jet.registration.repository.RegistrationRepository;
import com.kad.jet.semester.repository.SemesterRepository;
import com.kad.jet.student.repository.StudentProfileRepository;
import com.kad.jet.user.model._User;
import com.kad.jet.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RegistrationServiceImp implements RegistrationService{

    private final RegistrationRepository registrationRepository;
    private final RegistrationItemRepository registrationItemRepository;
    private final CourseRepository courseRepository;
    private final SemesterRepository semesterRepository;
    private final StudentGradeRepository studentGradeRepository;
    private final StudentProfileRepository studentProfileRepository;
    //private final TuitionAccountRepository tuitionAccountRepository;
    private final FeeStructureRepository feeStructureRepository;
    private final AdminChargeRepository adminChargeRepository;
    //private final CreditBalanceRepository creditBalanceRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public RegistrationResponse createDraft(_User currentUser, CreateRegistrationRequest request) {

    }

    @Override
    public RegistrationResponse getRegistrationById(_User currentUser, UUID id) {
        return null;
    }

    @Override
    public Page<RegistrationResponse> getAllRegistrations(_User currentUser, Pageable pageable) {
        return null;
    }

    @Override
    public Page<RegistrationResponse> getPendingRegistrations(_User currentUser, Pageable pageable) {
        return null;
    }

    @Override
    public List<RegistrationResponse> getMyRegistrations(_User currentUser) {
        return List.of();
    }

    @Override
    @Transactional
    public RegistrationResponse addCourse(_User currentUser, UUID registrationId, AddCourseRequest request) {
        return null;
    }

    @Override
    @Transactional
    public RegistrationResponse removeCourse(_User currentUser, UUID registrationId, UUID courseId) {
        return null;
    }

    @Override
    public List<RegistrationItemResponse> getRegistrationCourses(_User currentUser, UUID registrationId) {
        return List.of();
    }

    @Override
    @Transactional
    public RegistrationResponse submitRegistration(_User currentUser, UUID registrationId, SubmitRegistrationRequest request) {
        return null;
    }

    @Override
    @Transactional
    public RegistrationResponse approveRegistration(_User currentUser, UUID registrationId, DeanReviewRequest request) {
        return null;
    }

    @Override
    @Transactional
    public RegistrationResponse rejectRegistration(_User currentUser, UUID registrationId, DeanReviewRequest request) {
        return null;
    }

    @Override
    public TuitionEstimateResponse getTuitionEstimate(_User currentUser, UUID registrationId) {
        return null;
    }
}
