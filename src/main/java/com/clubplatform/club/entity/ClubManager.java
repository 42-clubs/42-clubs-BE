package com.clubplatform.club.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 부원 관계·전역 역할과 독립적인 동아리별 회장 권한. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "club_managers",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_club_managers_user_club",
                        columnNames = {"user_id", "club_id"}))
public class ClubManager {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "club_id", nullable = false)
    private Long clubId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ClubManager(long userId, long clubId) {
        if (userId <= 0 || clubId <= 0) {
            throw new IllegalArgumentException("사용자와 동아리 ID는 양수여야 합니다.");
        }
        this.userId = userId;
        this.clubId = clubId;
    }

    @PrePersist
    private void onCreate() {
        createdAt = Instant.now();
    }
}
