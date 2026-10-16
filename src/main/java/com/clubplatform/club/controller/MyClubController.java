package com.clubplatform.club.controller;

import com.clubplatform.club.dto.*;
import com.clubplatform.club.service.*;
import com.clubplatform.global.dto.PageResponse;
import com.clubplatform.global.security.AuthenticatedUser;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class MyClubController {
    private final ClubSelfQueryService clubs;
    private final ClubPermissionService permissions;

    @GetMapping("/api/v1/me/clubs")
    public PageResponse<MyClubResponse> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return clubs.list(user.userId(), page, size);
    }

    @GetMapping("/api/v1/clubs/{clubId}/permissions/me")
    public ClubPermissions permissions(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable @Min(1) long clubId) {
        return permissions.permissions(user.userId(), clubId);
    }
}
