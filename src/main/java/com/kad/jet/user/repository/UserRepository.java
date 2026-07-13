package com.kad.jet.user.repository;

import com.kad.jet.user.model.UserRole;
import com.kad.jet.user.model._User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<_User, UUID> {
    Optional<_User> findByEmailIgnoreCase(String email);

    boolean existsByEmail(String email);

    List<_User> findByUserRoleAndIsActiveTrue(UserRole userRole);
}
