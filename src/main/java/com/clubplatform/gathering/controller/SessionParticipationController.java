package com.clubplatform.gathering.controller;

import com.clubplatform.gathering.dto.*;
import com.clubplatform.gathering.service.SessionParticipationService;
import com.clubplatform.global.dto.PageResponse;
import com.clubplatform.global.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/gatherings/{gatheringId}/sessions/{sessionId}/participants")
public class SessionParticipationController {
    private final SessionParticipationService service;

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void join(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable("gatheringId") @Min(1) long gatheringId,
            @PathVariable("sessionId") @Min(1) long id) {
        service.join(actor.userId(), gatheringId, id);
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable("gatheringId") @Min(1) long gatheringId,
            @PathVariable("sessionId") @Min(1) long id) {
        service.cancel(actor.userId(), gatheringId, id);
    }

    @GetMapping("/me")
    public ParticipationResponse mine(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable("gatheringId") @Min(1) long gatheringId,
            @PathVariable("sessionId") @Min(1) long id) {
        return service.mine(actor.userId(), gatheringId, id);
    }

    @GetMapping
    public PageResponse<ParticipantResponse> roster(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable("gatheringId") @Min(1) long gatheringId,
            @PathVariable("sessionId") @Min(1) long id,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.roster(actor.userId(), gatheringId, id, page, size);
    }

    @PatchMapping("/{userId}/attendance")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void attendance(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable("gatheringId") @Min(1) long gatheringId,
            @PathVariable("sessionId") @Min(1) long id,
            @PathVariable("userId") @Min(1) long userId,
            @RequestBody @Valid AttendanceRequest request) {
        service.attendance(actor.userId(), gatheringId, id, userId, request.attendanceStatus());
    }
}
