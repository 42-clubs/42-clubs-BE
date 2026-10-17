package com.clubplatform.club.controller;

import com.clubplatform.club.dto.ClubListResponse;
import com.clubplatform.club.dto.ClubUpdateRequest;
import com.clubplatform.club.entity.OperationStatus;
import com.clubplatform.club.service.ClubQueryService;
import com.clubplatform.club.service.ClubUpdateService;
import com.clubplatform.global.security.AuthenticatedUser;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 동아리 공개 목록과 필드별 권한에 따른 기본정보 수정을 제공한다. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/clubs")
public class ClubController {

    private final ClubQueryService clubQueryService;
    private final ClubUpdateService clubUpdateService;

    @PatchMapping("/{clubId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable("clubId") @Min(1) long clubId,
            @RequestBody ClubUpdateRequest request) {
        clubUpdateService.update(actor.userId(), clubId, request);
    }

    /** 운영 상태 필터와 페이지 조건을 받아 동아리 목록을 반환한다. */
    @GetMapping
    public ClubListResponse findClubs(
            @RequestParam(name = "operationStatus", required = false)
                    OperationStatus operationStatus,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size) {
        return clubQueryService.findClubs(operationStatus, page, size);
    }
}
