package com.clubplatform.club.dto;

import com.clubplatform.club.entity.Club;
import com.clubplatform.club.entity.OperationStatus;
import com.clubplatform.club.entity.RecruitmentStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** 동아리 기본 정보·모집 안내·생성 및 수정 시각을 담는 공개 상세 응답. */
public record ClubDetailResponse(
        Long id,
        String name,
        String summary,
        String description,
        OperationStatus operationStatus,
        List<ManagerResponse> managers,
        RecruitmentResponse recruitment,
        Instant createdAt,
        Instant updatedAt) {

    /** 동아리 엔티티와 조회 기준일을 공개 상세 응답으로 변환한다. */
    public static ClubDetailResponse from(
            Club club, LocalDate today, List<ManagerResponse> managers) {
        return new ClubDetailResponse(
                club.getId(),
                club.getName(),
                club.getSummary(),
                club.getDescription(),
                club.getOperationStatus(),
                managers,
                new RecruitmentResponse(
                        club.isRecruitmentPlanned(),
                        club.recruitmentStatusOn(today),
                        club.getRecruitmentStartDate(),
                        club.getRecruitmentEndDate(),
                        club.isRecruitmentClosedEarly(),
                        club.getApplicationMethod(),
                        club.getApplicationUrl()),
                club.getCreatedAt(),
                club.getUpdatedAt());
    }

    /** 현재 회장의 로그인명과 표시 이름만 담는 공개 응답. */
    public record ManagerResponse(String intraLogin, String displayName) {}

    /** 모집 계획 여부·계산된 상태·일정·지원 방법을 담는 모집 안내. */
    public record RecruitmentResponse(
            boolean planned,
            RecruitmentStatus status,
            LocalDate startDate,
            LocalDate endDate,
            boolean closedEarly,
            String applicationMethod,
            String applicationUrl) {}
}
