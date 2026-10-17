package com.clubplatform.club.entity;

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
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 동아리 기본 정보·운영 상태·모집 계획을 보관하고 모집 상태를 계산한다. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "clubs")
public class Club {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 200)
    private String summary;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_status", nullable = false, length = 20)
    private OperationStatus operationStatus;

    @Column(name = "recruitment_planned", nullable = false)
    private boolean recruitmentPlanned;

    @Column(name = "recruitment_start_date")
    private LocalDate recruitmentStartDate;

    @Column(name = "recruitment_end_date")
    private LocalDate recruitmentEndDate;

    @Column(name = "recruitment_closed_early", nullable = false)
    private boolean recruitmentClosedEarly;

    @Column(name = "application_method", columnDefinition = "text")
    private String applicationMethod;

    @Column(name = "application_url", length = 2048)
    private String applicationUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** 기본 정보를 검증하고 모집 계획이 없는 운영 중 동아리를 생성한다. */
    public Club(String name, String summary, String description) {
        this.name = requireText(name, 100);
        this.summary = requireText(summary, 200);
        this.description = requireText(description, Integer.MAX_VALUE);
        this.operationStatus = OperationStatus.ACTIVE;
    }

    /** 권한 검사 뒤 제공한 필드만 변경하며 검증이 끝나기 전에는 값을 대입하지 않는다. */
    public void updateBasicInfo(String name, String summary, String description) {
        String nextName = name == null ? this.name : requireText(name, 100);
        String nextSummary = summary == null ? this.summary : requireText(summary, 200);
        String nextDescription =
                description == null
                        ? this.description
                        : requireText(description, Integer.MAX_VALUE);
        this.name = nextName;
        this.summary = nextSummary;
        this.description = nextDescription;
    }

    /** 운영 상태와 모집 계획·일정을 기준으로 주어진 날짜의 모집 상태를 계산한다. */
    public RecruitmentStatus recruitmentStatusOn(LocalDate date) {
        if (!recruitmentPlanned) {
            return RecruitmentStatus.NOT_RECRUITING;
        }
        if (recruitmentClosedEarly || operationStatus != OperationStatus.ACTIVE) {
            return RecruitmentStatus.CLOSED;
        }
        if (date.isBefore(recruitmentStartDate)) {
            return RecruitmentStatus.SCHEDULED;
        }
        if (recruitmentEndDate != null && date.isAfter(recruitmentEndDate)) {
            return RecruitmentStatus.CLOSED;
        }
        return RecruitmentStatus.OPEN;
    }

    /** 필수 문자열이 비어 있지 않고 허용 길이 이내인지 검증한다. */
    private static String requireText(String value, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException("동아리 정보는 허용 길이 내의 비어 있지 않은 값이어야 합니다.");
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
