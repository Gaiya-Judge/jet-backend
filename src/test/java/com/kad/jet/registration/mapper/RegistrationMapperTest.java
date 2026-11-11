package com.kad.jet.registration.mapper;

import com.kad.jet.registration.model.Registration;
import com.kad.jet.registration.model.RegistrationStatus;
import com.kad.jet.semester.model.Semester;
import com.kad.jet.semester.model.SemesterType;
import com.kad.jet.user.model._User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RegistrationMapperTest {

    @Test
    void mapsTuitionAccountIdAndPreservesExistingRegistrationFields() {
        UUID studentId = UUID.randomUUID();
        UUID semesterId = UUID.randomUUID();
        UUID tuitionId = UUID.randomUUID();
        Registration registration = registration(studentId, semesterId);

        var response = RegistrationMapper.toDto(registration, "MAT-01", List.of(), tuitionId);

        assertEquals(tuitionId, response.getTuitionAccountId());
        assertEquals(registration.getId(), response.getId());
        assertEquals(studentId, response.getStudentId());
        assertEquals("Student Name", response.getStudentName());
        assertEquals("MAT-01", response.getMatricNumber());
        assertEquals(RegistrationStatus.DRAFT, response.getStatus());
        assertEquals(0, response.getTotalCreditUnits());
    }

    @Test
    void mapsMissingTuitionAccountAsNull() {
        Registration registration = registration(UUID.randomUUID(), UUID.randomUUID());

        var response = RegistrationMapper.toDto(registration, "MAT-01", List.of(), null);

        assertNull(response.getTuitionAccountId());
    }

    @Test
    void serializesMissingTuitionAccountAsExplicitNull() throws Exception {
        Registration registration = registration(UUID.randomUUID(), UUID.randomUUID());
        var response = RegistrationMapper.toDto(registration, "MAT-01", List.of(), null);

        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        var json = objectMapper.readTree(objectMapper.writeValueAsBytes(response));
        org.junit.jupiter.api.Assertions.assertTrue(json.has("tuitionAccountId"));
        org.junit.jupiter.api.Assertions.assertTrue(json.get("tuitionAccountId").isNull());
    }

    private Registration registration(UUID studentId, UUID semesterId) {
        return Registration.builder().id(UUID.randomUUID())
                .student(_User.builder().id(studentId).fullName("Student Name").build())
                .semester(Semester.builder().id(semesterId).academicYear("2026/2027")
                        .semesterType(SemesterType.FIRST).build())
                .status(RegistrationStatus.DRAFT).totalCreditUnits(0).build();
    }
}
