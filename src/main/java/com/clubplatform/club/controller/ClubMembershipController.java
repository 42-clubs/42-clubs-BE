package com.clubplatform.club.controller;

import com.clubplatform.club.dto.MembershipResponse;
import com.clubplatform.club.service.ClubMembershipService;
import com.clubplatform.global.security.AuthenticatedUser;
import com.clubplatform.user.dto.UserDirectoryPage;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ClubMembershipController {
    private final ClubMembershipService service;

    @GetMapping("/api/v1/admin/users/{userId}/clubs")
    public MembershipResponse memberships(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable("userId") @Min(1) long userId) {
        return service.memberships(actor.userId(), userId);
    }

    @PutMapping("/api/v1/admin/clubs/{clubId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void register(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable("clubId") @Min(1) long clubId,
            @PathVariable("userId") @Min(1) long userId) {
        service.register(actor.userId(), clubId, userId);
    }

    @GetMapping("/api/v1/clubs/{clubId}/members")
    public UserDirectoryPage roster(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable("clubId") @Min(1) long clubId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.roster(actor.userId(), clubId, page, size);
    }
}
