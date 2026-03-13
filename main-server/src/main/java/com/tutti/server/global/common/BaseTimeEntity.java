package com.tutti.server.global.common;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 모든 엔티티의 공통 감사(Audit) 필드를 제공하는 추상 클래스.
 *
 * <p>
 * 이 클래스를 상속하면 {@code createdAt}과 {@code updatedAt} 필드가
 * 자동으로 추가되고, 엔티티가 저장/수정될 때 JPA가 시각을 자동으로 기록합니다.
 * </p>
 *
 * <h3>사용 방법</h3>
 * 
 * <pre>
 * public class Profile extends BaseTimeEntity {
 *     // createdAt, updatedAt이 자동으로 포함됩니다.
 * }
 * </pre>
 *
 * <h3>동작 원리</h3>
 * <ul>
 * <li>{@code @MappedSuperclass}: JPA에게 "이 클래스는 테이블이 아니라,
 * 자식 클래스의 컬럼을 제공하는 역할"임을 알려줍니다.</li>
 * <li>{@code @EntityListeners(AuditingEntityListener.class)}: JPA의 이벤트 리스너가
 * 엔티티 저장 전/수정 전에 시각을 자동으로 채워줍니다.</li>
 * <li>{@code @CreatedDate}: 엔티티가 처음 DB에 INSERT될 때 현재 시각이 기록됩니다.</li>
 * <li>{@code @LastModifiedDate}: 엔티티가 UPDATE될 때마다 시각이 갱신됩니다.</li>
 * </ul>
 *
 * <p>
 * <b>필수 조건:</b> 이 기능이 동작하려면 메인 애플리케이션 클래스에
 * {@code @EnableJpaAuditing}이 선언되어 있어야 합니다.
 * ({@link com.tutti.server.TuttiApplication} 참조)
 * </p>
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
public abstract class BaseTimeEntity {

    /** 생성 시각 — INSERT 시 자동 기록. 한번 설정되면 변경되지 않습니다. */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 수정 시각 — UPDATE 시 자동 갱신. 이름 변경, 상태 전이 등 모든 수정에 반응합니다. */
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
