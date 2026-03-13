package com.tutti.server.domain.user.repository;

import com.tutti.server.domain.user.entity.Profile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProfileRepository extends JpaRepository<Profile, UUID> {

    Optional<Profile> findByEmail(String email);

    Optional<Profile> findByIdAndIsActive(UUID id, boolean isActive);

    boolean existsByEmail(String email);
}
