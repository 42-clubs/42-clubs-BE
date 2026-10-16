package com.clubplatform.mypage.controller;

import com.clubplatform.global.dto.PageResponse;
import com.clubplatform.global.security.AuthenticatedUser;
import com.clubplatform.mypage.dto.*;
import com.clubplatform.mypage.service.MyPageQueryService;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class MyPageController {
    private final MyPageQueryService queries;

    @GetMapping("/api/v1/me/participations")
    public PageResponse<MyParticipationGroup> participations(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "UPCOMING") ParticipationPeriod period,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return queries.participations(user.userId(), period, page, size);
    }

    @GetMapping("/api/v1/me/gatherings")
    public PageResponse<MyParticipationGroup> gatherings(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "ACTIVE") GatheringScope scope,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return queries.gatherings(user.userId(), scope, page, size);
    }
}
