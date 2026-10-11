package com.clubplatform.club.dto;

import com.clubplatform.club.entity.Club;
import com.clubplatform.club.entity.OperationStatus;
import com.clubplatform.club.entity.RecruitmentStatus;
import java.time.LocalDate;

/** 동아리 목록에 공개할 요약 정보와 모집 상태. */
public record ClubSummaryResponse(
        Long id,
        String name,
        String summary,
        OperationStatus operationStatus,
        RecruitmentStatus recruitmentStatus) {

    /** 동아리 엔티티와 조회 기준일을 목록 항목 응답으로 변환한다. */
    public static ClubSummaryResponse from(Club club, LocalDate today) {
        return new ClubSummaryResponse(
                club.getId(),
                club.getName(),
                club.getSummary(),
                club.getOperationStatus(),
                club.recruitmentStatusOn(today));
    }
}
