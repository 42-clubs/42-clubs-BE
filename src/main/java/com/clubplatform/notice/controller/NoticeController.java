package com.clubplatform.notice.controller;

import com.clubplatform.global.dto.*;
import com.clubplatform.global.security.AuthenticatedUser;
import com.clubplatform.notice.dto.*;
import com.clubplatform.notice.service.NoticeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class NoticeController {
    private final NoticeService service;

    @PostMapping("/api/v1/clubs/{clubId}/notices")
    public ResponseEntity<CreatedResponse> create(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable("clubId") @Min(1) long clubId,
            @RequestBody @Valid NoticeRequest request) {
        long id = service.create(actor.userId(), clubId, request);
        return ResponseEntity.created(URI.create("/api/v1/notices/" + id))
                .body(new CreatedResponse(id));
    }

    @GetMapping("/api/v1/clubs/{clubId}/notices")
    public PageResponse<NoticeSummary> list(
            @PathVariable("clubId") @Min(1) long clubId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(clubId, page, size);
    }

    @GetMapping("/api/v1/notices/{noticeId}")
    public NoticeResponse detail(@PathVariable("noticeId") @Min(1) long noticeId) {
        return service.detail(noticeId);
    }
}
