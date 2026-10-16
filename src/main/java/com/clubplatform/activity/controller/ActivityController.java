package com.clubplatform.activity.controller;

import com.clubplatform.activity.dto.*;
import com.clubplatform.activity.service.*;
import com.clubplatform.global.dto.*;
import com.clubplatform.global.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class ActivityController {
    private final ActivityCreationService creation;
    private final ActivityQueryService query;

    @PostMapping(
            value = "/api/v1/clubs/{clubId}/activities",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CreatedResponse> create(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable("clubId") @Min(1) long clubId,
            @RequestPart("activity") @Valid ActivityRequest request,
            @RequestPart(value = "images", required = false) List<MultipartFile> images) {
        long id = creation.create(actor.userId(), clubId, request, images);
        return ResponseEntity.created(URI.create("/api/v1/clubs/" + clubId + "/activities/" + id))
                .body(new CreatedResponse(id));
    }

    @GetMapping("/api/v1/clubs/{clubId}/activities")
    public PageResponse<ActivityResponse> list(
            @PathVariable("clubId") @Min(1) long clubId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return query.list(clubId, page, size);
    }

    @GetMapping("/api/v1/clubs/{clubId}/activities/{activityId}")
    public ActivityResponse detail(
            @PathVariable("clubId") @Min(1) long clubId,
            @PathVariable("activityId") @Min(1) long id) {
        return query.detail(clubId, id);
    }
}
