package com.kad.jet.registration.repository;

import com.kad.jet.finance.model.CreditBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CreditBalanceRepository extends JpaRepository<CreditBalance, UUID> {
}
