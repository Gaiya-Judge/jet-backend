package com.kad.jet.student.service;

import com.kad.jet.student.dto.StudentAccountCreationRequest;
import com.kad.jet.student.dto.StudentProfileResponse;
import com.kad.jet.user.model._User;

import java.util.UUID;

public interface StudentProfileService {

    StudentProfileResponse createStudentAccount(_User currentUser, StudentAccountCreationRequest request);


    StudentProfileResponse getStudentProfileById(UUID studentId, _User currentUser);
}
