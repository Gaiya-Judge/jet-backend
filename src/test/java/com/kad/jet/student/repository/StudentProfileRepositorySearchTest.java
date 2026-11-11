package com.kad.jet.student.repository;

import com.kad.jet.department.model.Department;
import com.kad.jet.department.repository.DepartmentRepository;
import com.kad.jet.program.model.Program;
import com.kad.jet.program.model.ProgramLevelType;
import com.kad.jet.program.repository.ProgramRepository;
import com.kad.jet.student.model.StudentProfile;
import com.kad.jet.user.model.UserRole;
import com.kad.jet.user.model._User;
import com.kad.jet.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class StudentProfileRepositorySearchTest {

    @Autowired private StudentProfileRepository studentProfileRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProgramRepository programRepository;
    @Autowired private DepartmentRepository departmentRepository;

    @Test
    void filteredAndUnfilteredDirectorySearchesReturnPagedResults() {
        String marker = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Program program = programRepository.save(Program.builder()
                .name("Search Test Program " + marker)
                .levelType(ProgramLevelType.UNDERGRADUATE)
                .isActive(true)
                .build());
        Department department = departmentRepository.save(Department.builder()
                .name("Search Test Department " + marker)
                .program(program)
                .isActive(true)
                .build());

        saveStudent(marker, "Needle Name", "needle", program, department);
        saveStudent(marker, "Another Student", "other", program, department);
        saveStudent(marker, "Third Student", "third", program, department);

        Page<StudentProfile> filtered = studentProfileRepository.searchDirectory(
                "NEEDLE", PageRequest.of(0, 1));

        assertEquals(1, filtered.getTotalElements());
        assertEquals(1, filtered.getSize());
        assertEquals(1, filtered.getNumberOfElements());
        assertTrue(filtered.getContent().getFirst().getMatricNumber().contains(marker));
        assertTrue(filtered.getContent().getFirst().getUser().getEmail().contains(marker));
        assertTrue(filtered.getContent().getFirst().getDepartment().getId().equals(department.getId()));
        assertTrue(filtered.getContent().getFirst().getProgram().getId().equals(program.getId()));

        Page<StudentProfile> unfiltered = studentProfileRepository.searchDirectory(
                null, PageRequest.of(0, 1));

        assertEquals(studentProfileRepository.count(), unfiltered.getTotalElements());
        assertEquals(1, unfiltered.getSize());
        assertEquals(1, unfiltered.getNumberOfElements());
        assertTrue(unfiltered.getContent().stream().allMatch(profile -> profile.getUser() != null));
    }

    private StudentProfile saveStudent(String marker, String fullName, String emailPrefix,
                                       Program program, Department department) {
        _User user = userRepository.save(_User.builder()
                .fullName(fullName + " " + marker)
                .email(emailPrefix + "-" + marker + "@example.test")
                .password("integration-test-hash")
                .userRole(UserRole.STUDENT)
                .isActive(true)
                .isBanned(false)
                .build());
        return studentProfileRepository.save(StudentProfile.builder()
                .user(user)
                .matricNumber("MAT-" + marker + "-" + emailPrefix)
                .level(100)
                .enrollmentYear(2026)
                .program(program)
                .department(department)
                .build());
    }
}
