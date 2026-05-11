package com.kad.jet.finance.repository;

import com.kad.jet.finance.model.CreditBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CreditBalanceRepository extends JpaRepository<CreditBalance, UUID> {
    List<CreditBalance> findByStudentIdAndIsAppliedFalse(UUID id);
}
