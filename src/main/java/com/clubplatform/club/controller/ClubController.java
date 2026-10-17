package com.clubplatform.club.controller;

import com.clubplatform.club.dto.ClubListResponse;
import com.clubplatform.club.entity.OperationStatus;
import com.clubplatform.club.service.ClubQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 동아리 공개 목록 요청을 검증하고 조회 서비스를 호출한다. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/clubs")
public class ClubController {

    private final ClubQueryService clubQueryService;

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
