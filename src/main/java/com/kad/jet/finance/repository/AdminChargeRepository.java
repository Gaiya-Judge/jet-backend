package com.kad.jet.finance.repository;

import com.kad.jet.finance.model.AdminCharge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AdminChargeRepository extends JpaRepository<AdminCharge, UUID> {

    List<AdminCharge> findBySemesterId(UUID semesterId);

    boolean existsBySemesterIdAndNameIgnoreCase(UUID semesterId, String name);
}
