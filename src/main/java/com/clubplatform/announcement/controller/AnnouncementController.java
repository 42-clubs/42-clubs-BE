package com.clubplatform.announcement.controller;

import com.clubplatform.announcement.dto.*;
import com.clubplatform.announcement.service.AnnouncementService;
import com.clubplatform.global.dto.*;
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
@RequestMapping("/api/v1/announcements")
public class AnnouncementController {
    private final AnnouncementService service;

    @PostMapping
    public ResponseEntity<CreatedResponse> create(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @RequestBody @Valid AnnouncementRequest request) {
        long id = service.create(actor.userId(), request);
        return ResponseEntity.created(URI.create("/api/v1/announcements/" + id))
                .body(new CreatedResponse(id));
    }

    @GetMapping
    public PageResponse<AnnouncementSummary> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(page, size);
    }

    @GetMapping("/{announcementId}")
    public AnnouncementResponse detail(@PathVariable("announcementId") @Min(1) long id) {
        return service.detail(id);
    }
}
