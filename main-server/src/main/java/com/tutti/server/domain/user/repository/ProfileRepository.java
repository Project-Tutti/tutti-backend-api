package com.tutti.server.domain.user.repository;

import com.tutti.server.domain.user.entity.Profile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProfileRepository extends JpaRepository<Profile, UUID> {

    Optional<Profile> findByEmail(String email);

    Optional<Profile> findByIdAndIsActive(UUID id, boolean isActive);

    boolean existsByEmail(String email);

    /** 활성 계정만 이메일 존재 여부 확인 — 탈퇴한 이메일은 재사용 가능. */
    boolean existsByEmailAndIsActive(String email, boolean isActive);
}
