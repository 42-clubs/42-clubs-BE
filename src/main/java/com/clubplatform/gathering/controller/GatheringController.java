package com.clubplatform.gathering.controller;

import com.clubplatform.gathering.dto.*;
import com.clubplatform.gathering.service.GatheringService;
import com.clubplatform.global.dto.PageResponse;
import com.clubplatform.global.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class GatheringController {
    private final GatheringService service;

    @PostMapping("/api/v1/gatherings")
    public ResponseEntity<GatheringCreated> create(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @RequestBody @Valid GatheringRequest request) {
        var result = service.create(actor.userId(), request);
        return ResponseEntity.created(URI.create("/api/v1/gatherings/" + result.gatheringId()))
                .body(result);
    }

    @PostMapping("/api/v1/gatherings/{gatheringId}/sessions")
    public ResponseEntity<GatheringCreated> add(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable("gatheringId") @Min(1) long id,
            @RequestBody @Valid SessionRequest request) {
        var result = service.addSession(actor.userId(), id, request);
        return ResponseEntity.created(
                        URI.create("/api/v1/gatherings/" + id + "/sessions/" + result.sessionId()))
                .body(result);
    }

    @GetMapping("/api/v1/gatherings")
    public PageResponse<GatheringResponse> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(page, size);
    }

    @GetMapping("/api/v1/gatherings/{gatheringId}")
    public GatheringResponse detail(@PathVariable("gatheringId") @Min(1) long id) {
        return service.detail(id);
    }

    @GetMapping("/api/v1/gatherings/{gatheringId}/sessions")
    public PageResponse<SessionResponse> sessions(
            @PathVariable("gatheringId") @Min(1) long id,
            @RequestParam(defaultValue = "ALL") @Pattern(regexp = "ALL|UPCOMING|ONGOING|PAST")
                    String filter,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.sessions(id, filter, page, size);
    }

    @GetMapping("/api/v1/gatherings/{gatheringId}/sessions/{sessionId}")
    public SessionResponse session(
            @PathVariable("gatheringId") @Min(1) long gatheringId,
            @PathVariable("sessionId") @Min(1) long id) {
        return service.session(gatheringId, id);
    }
}
