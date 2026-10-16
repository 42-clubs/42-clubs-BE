package com.clubplatform.activity.controller;

import com.clubplatform.activity.dto.*;
import com.clubplatform.activity.service.ActivityParticipationService;
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
@RequestMapping("/api/v1/clubs/{clubId}/activities/{activityId}/participants")
public class ActivityParticipationController {
    private final ActivityParticipationService service;

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void join(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable("clubId") @Min(1) long clubId,
            @PathVariable("activityId") @Min(1) long id) {
        service.join(actor.userId(), clubId, id);
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable("clubId") @Min(1) long clubId,
            @PathVariable("activityId") @Min(1) long id) {
        service.cancel(actor.userId(), clubId, id);
    }

    @GetMapping("/me")
    public ParticipationResponse mine(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable("clubId") @Min(1) long clubId,
            @PathVariable("activityId") @Min(1) long id) {
        return service.mine(actor.userId(), clubId, id);
    }

    @GetMapping
    public PageResponse<ParticipantResponse> roster(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable("clubId") @Min(1) long clubId,
            @PathVariable("activityId") @Min(1) long id,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.roster(actor.userId(), clubId, id, page, size);
    }

    @PatchMapping("/{userId}/attendance")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void attendance(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable("clubId") @Min(1) long clubId,
            @PathVariable("activityId") @Min(1) long id,
            @PathVariable("userId") @Min(1) long userId,
            @RequestBody @Valid AttendanceRequest request) {
        service.attendance(actor.userId(), clubId, id, userId, request.attendanceStatus());
    }
}
