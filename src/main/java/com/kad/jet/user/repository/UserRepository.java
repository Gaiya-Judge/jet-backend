package com.kad.jet.user.repository;

import com.kad.jet.user.model._user;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<_user, UUID> {
    Optional<_user> findByEmail(String email);

    boolean existsByEmail(String email);
}
