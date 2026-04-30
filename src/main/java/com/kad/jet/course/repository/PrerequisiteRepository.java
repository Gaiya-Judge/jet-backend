package com.kad.jet.course.repository;

import com.kad.jet.course.model.Prerequisite;
import com.kad.jet.course.model.PrerequisiteId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PrerequisiteRepository extends JpaRepository<Prerequisite, PrerequisiteId> {

    List<Prerequisite> findByCourseId(UUID courseId);

    boolean existsById(PrerequisiteId id);
}
