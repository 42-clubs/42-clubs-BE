package com.clubplatform.clubdetail.controller;

import com.clubplatform.club.dto.ClubDetailResponse;
import com.clubplatform.clubdetail.service.ClubDetailQueryService;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 동아리 정보와 최근 활동을 합친 기존 공개 상세 경로. */
@RestController
@RequiredArgsConstructor
public class ClubDetailController {
    private final ClubDetailQueryService query;

    @GetMapping("/api/v1/clubs/{clubId}")
    public ClubDetailResponse findClub(@PathVariable("clubId") @Min(1) long clubId) {
        return query.findClub(clubId);
    }
}
