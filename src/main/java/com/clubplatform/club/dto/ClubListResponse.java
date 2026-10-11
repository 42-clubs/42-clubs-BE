package com.clubplatform.club.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/** 동아리 목록 항목과 페이지·전체 개수 정보를 담는 공개 응답. */
public record ClubListResponse(
        List<ClubSummaryResponse> content, int page, int size, long totalElements, int totalPages) {

    /** 조회된 페이지의 항목과 메타데이터를 API 응답으로 변환한다. */
    public static ClubListResponse from(Page<ClubSummaryResponse> clubs) {
        return new ClubListResponse(
                clubs.getContent(),
                clubs.getNumber(),
                clubs.getSize(),
                clubs.getTotalElements(),
                clubs.getTotalPages());
    }
}
