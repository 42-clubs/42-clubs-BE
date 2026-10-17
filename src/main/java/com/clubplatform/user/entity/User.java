package com.clubplatform.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 42 사용자 식별 정보·서비스 역할과 생성 및 수정 시각을 관리하는 엔티티. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "intra_id", nullable = false, unique = true)
    private Long intraId;

    @Column(name = "intra_login", nullable = false, length = 100)
    private String intraLogin;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** 42 사용자 정보의 유효성을 검증하고 일반 사용자 역할로 생성한다. */
    public User(Long intraId, String intraLogin, String displayName) {
        if (intraId == null || intraId <= 0) {
            throw new IllegalArgumentException("42 사용자 ID는 양수여야 합니다.");
        }
        this.intraId = intraId;
        this.intraLogin = requireName(intraLogin);
        this.displayName = requireName(displayName);
        this.role = UserRole.USER;
    }

    /** 서비스가 마지막 ADMIN 보호를 확인한 뒤 운영 역할을 변경한다. */
    public void changeAdminRole(boolean granted) {
        role = granted ? UserRole.ADMIN : UserRole.USER;
    }

    /** 사용자 이름이 비어 있지 않고 100자 이내인지 검증한다. */
    private static String requireName(String value) {
        if (value == null || value.isBlank() || value.length() > 100) {
            throw new IllegalArgumentException("사용자 이름은 1~100자의 비어 있지 않은 값이어야 합니다.");
        }
        return value;
    }

    /** 최초 영속화 시 생성 시각과 수정 시각을 설정한다. */
    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    /** 엔티티 갱신 시 수정 시각을 현재 시각으로 설정한다. */
    @PreUpdate
    private void onUpdate() {
        updatedAt = Instant.now();
    }
}
