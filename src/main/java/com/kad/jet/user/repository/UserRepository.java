package com.kad.jet.user.repository;

import com.kad.jet.user.model.UserRole;
import com.kad.jet.user.model._User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<_User, UUID> {
    Optional<_User> findByEmailIgnoreCase(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from _User u where lower(u.email) = lower(:email)")
    Optional<_User> findByEmailIgnoreCaseForUpdate(@Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from _User u where u.id = :id")
    Optional<_User> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByEmail(String email);

    List<_User> findByIsActiveTrue();

    List<_User> findByUserRoleAndIsActiveTrue(UserRole userRole);

    List<_User> findByUserRoleInAndIsActiveTrue(List<UserRole> userRoles);
}
