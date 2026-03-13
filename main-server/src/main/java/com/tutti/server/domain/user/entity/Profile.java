package com.tutti.server.domain.user.entity;

import com.tutti.server.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 사용자 프로필 엔티티 — DB의 "profiles" 테이블에 매핑됩니다.
 *
 * <h3>아키텍처 위치</h3>
 * 
 * <pre>
 * [Client] → Controller → Service → Repository → 이 엔티티 ←→ DB(profiles 테이블)
 * </pre>
 *
 * <h3>설계 배경</h3>
 * <ul>
 * <li>Supabase의 {@code auth.users} 테이블을 확장하는 "프로필" 역할을 합니다.</li>
 * <li>인증(Authentication) 자체는 Supabase Auth가 담당하고, 이 테이블은
 * 앱 고유의 사용자 정보(이름, 아바타, 활성 상태 등)를 저장합니다.</li>
 * <li>{@code password} 필드는 Supabase Auth 전환 완료 전까지 임시로 유지합니다.
 * 전환 후에는 이 필드를 제거해야 합니다.</li>
 * </ul>
 *
 * <h3>주요 비즈니스 규칙</h3>
 * <ul>
 * <li>회원 탈퇴 시 물리 삭제가 아닌 <b>Soft Delete</b> 방식을 사용합니다.
 * ({@code isActive = false}, {@code deletedAt} 설정)</li>
 * <li>탈퇴한 사용자는 로그인 시 {@code ACCOUNT_DISABLED} 에러를 반환합니다.</li>
 * </ul>
 *
 * @see com.tutti.server.domain.user.service.UserService
 * @see com.tutti.server.domain.auth.service.AuthService
 */
@Entity
// @Table: 이 클래스가 DB의 "profiles" 테이블에 매핑됨을 선언합니다.
// JPA(Hibernate)가 이 정보를 기반으로 SQL을 자동 생성합니다.
@Table(name = "profiles")
// @Getter: Lombok이 모든 필드의 getter 메서드를 자동 생성합니다.
@Getter
// @NoArgsConstructor(PROTECTED): JPA는 기본 생성자를 필요로 합니다.
// PROTECTED로 설정하여 외부에서 직접 new Profile()을 호출하지 못하게 막습니다.
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
// @Builder: Profile.builder().email(...).name(...).build() 형태로 객체 생성을 지원합니다.
// Entity를 생성할 때 setter 대신 Builder 패턴을 사용하여 불변성을 보장합니다.
@Builder
public class Profile extends BaseTimeEntity {

    /**
     * Primary Key — UUID v7 기반 자동 생성.
     * Supabase의 auth.users.id와 동일한 값을 사용하여 외래키로 연결됩니다.
     *
     * @UuidGenerator: Hibernate가 UUID를 자동으로 생성해줍니다 (DB INSERT 시).
     */
    @Id
    @UuidGenerator
    @Column(name = "user_id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    /** 로그인용 이메일 — unique 제약조건으로 중복 방지. */
    @Column(nullable = false, unique = true, length = 255)
    private String email;

    /** 사용자 표시 이름 (UI에 표시되는 닉네임). */
    @Column(nullable = false, length = 50)
    private String name;

    /**
     * 비밀번호 해시 — BCrypt로 인코딩된 값 저장.
     * 소셜 로그인 사용자는 이 필드가 null입니다.
     * Supabase Auth 전환 후에는 이 필드를 제거할 예정입니다.
     */
    @Column(length = 255)
    private String password;

    /**
     * 인증 제공자 — EMAIL(자체 회원가입) 또는 GOOGLE(소셜 로그인).
     *
     * @Enumerated(STRING): Enum 값을 DB에 문자열("EMAIL", "GOOGLE")로 저장합니다.
     * ORDINAL(숫자)로 저장하면 Enum 순서 변경 시 데이터가 깨지므로 STRING을 사용합니다.
     * 
     * @Builder.Default: Builder로 생성 시 기본값을 EMAIL로 설정합니다.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Provider provider = Provider.EMAIL;

    /** 프로필 이미지 URL — 소셜 로그인 시 OAuth 제공자로부터 받아옵니다. */
    @Column(length = 512)
    private String avatarUrl;

    /**
     * 계정 활성 상태.
     * true = 정상 계정, false = 탈퇴(Soft Delete)된 계정.
     * Repository 쿼리에서 이 값으로 탈퇴 사용자를 필터링합니다.
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    /**
     * 탈퇴 시각 — Soft Delete 시 현재 시각이 기록됩니다.
     * null이면 활성 계정, 값이 있으면 탈퇴된 계정입니다.
     */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // ── Enum 정의 ──

    /**
     * 인증 제공자 종류.
     * 새로운 소셜 로그인(Apple, Kakao 등)을 추가할 때 여기에 값을 추가합니다.
     */
    public enum Provider {
        EMAIL, GOOGLE
    }

    // ── 비즈니스 메서드 ──
    // Entity 내부에 비즈니스 로직을 두어 "Rich Domain Model"을 구현합니다.
    // Service에서 profile.setActive(false) 같은 setter를 쓰는 대신,
    // profile.softDelete()처럼 의미 있는 도메인 메서드를 호출합니다.

    /**
     * 회원 탈퇴 처리 (Soft Delete).
     * 물리 삭제(DB에서 완전 삭제)가 아닌 논리 삭제를 수행합니다.
     * 왜? → 탈퇴 후에도 사용자의 프로젝트 데이터를 일정 기간 보존하고,
     * 악의적 재가입 방지 및 CS 대응을 위해 기록을 남겨야 하기 때문입니다.
     */
    public void softDelete() {
        this.isActive = false;
        this.deletedAt = LocalDateTime.now();
    }

    /** 활성 상태 확인. Lombok @Getter가 isIsActive()를 생성하는 문제를 방지합니다. */
    public boolean isActive() {
        return this.isActive;
    }
}
