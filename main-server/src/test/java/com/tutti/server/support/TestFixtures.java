package com.tutti.server.support;

import com.tutti.server.domain.auth.entity.RefreshToken;
import com.tutti.server.domain.project.entity.Project;
import com.tutti.server.domain.project.entity.ProjectVersion;
import com.tutti.server.domain.user.entity.Profile;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 테스트용 엔티티 Fixture 팩토리.
 * 반복되는 Mock 데이터 생성 로직을 집중 관리합니다.
 */
public final class TestFixtures {

    public static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID OTHER_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    public static final String EMAIL = "test@tutti.com";
    public static final String NAME = "테스트유저";
    public static final String PASSWORD = "encodedPassword123!";
    public static final String RAW_PASSWORD = "Test123!@";

    private TestFixtures() {
    }

    // ── Profile ──

    public static Profile createActiveProfile() {
        Profile profile = Profile.builder()
                .email(EMAIL)
                .name(NAME)
                .password(PASSWORD)
                .provider(Profile.Provider.EMAIL)
                .build();
        ReflectionTestUtils.setField(profile, "id", USER_ID);
        return profile;
    }

    public static Profile createActiveProfile(UUID userId, String email) {
        Profile profile = Profile.builder()
                .email(email)
                .name(NAME)
                .password(PASSWORD)
                .provider(Profile.Provider.EMAIL)
                .build();
        ReflectionTestUtils.setField(profile, "id", userId);
        return profile;
    }

    public static Profile createDeactivatedProfile() {
        Profile profile = createActiveProfile();
        profile.softDelete();
        return profile;
    }

    // ── Project ──

    public static Project createProject(Profile owner) {
        Project project = Project.builder()
                .user(owner)
                .name("테스트 프로젝트")
                .originalFileName("test.mid")
                .midiFilePath("/uploads/midi/test.mid")
                .build();
        ReflectionTestUtils.setField(project, "id", 1L);
        ReflectionTestUtils.setField(project, "createdAt", LocalDateTime.now());
        return project;
    }

    public static Project createDeletedProject(Profile owner) {
        Project project = createProject(owner);
        project.softDelete();
        return project;
    }

    // ── ProjectVersion ──

    public static ProjectVersion createPendingVersion(Project project) {
        ProjectVersion version = ProjectVersion.builder()
                .project(project)
                .name("Ver 1")
                .status(ProjectVersion.VersionStatus.PENDING)
                .build();
        ReflectionTestUtils.setField(version, "id", 1L);
        ReflectionTestUtils.setField(version, "createdAt", LocalDateTime.now());
        return version;
    }

    public static ProjectVersion createCompleteVersion(Project project) {
        ProjectVersion version = ProjectVersion.builder()
                .project(project)
                .name("Ver 1")
                .status(ProjectVersion.VersionStatus.COMPLETE)
                .progress(100)
                .resultMidiPath("/results/result.mid")
                .resultXmlPath("/results/result.xml")
                .resultPdfPath("/results/result.pdf")
                .build();
        ReflectionTestUtils.setField(version, "id", 1L);
        ReflectionTestUtils.setField(version, "createdAt", LocalDateTime.now());
        return version;
    }

    public static ProjectVersion createDeletedVersion(Project project) {
        ProjectVersion version = createPendingVersion(project);
        version.softDelete();
        return version;
    }

    // ── RefreshToken ──

    public static RefreshToken createValidRefreshToken(UUID userId, String tokenHash) {
        RefreshToken token = RefreshToken.builder()
                .userId(userId)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        ReflectionTestUtils.setField(token, "id", UUID.randomUUID());
        return token;
    }

    public static RefreshToken createExpiredRefreshToken(UUID userId, String tokenHash) {
        RefreshToken token = RefreshToken.builder()
                .userId(userId)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().minusSeconds(3600))
                .build();
        ReflectionTestUtils.setField(token, "id", UUID.randomUUID());
        return token;
    }
}
