package com.clubplatform.club.controller;

import com.clubplatform.club.service.ClubManagerAdministrationService;
import com.clubplatform.global.security.AuthenticatedUser;
import com.clubplatform.user.dto.UserDirectoryPage;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/clubs/{clubId}/managers")
public class ClubManagerAdministrationController {
    private final ClubManagerAdministrationService managers;

    @GetMapping
    public UserDirectoryPage list(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable("clubId") @Min(1) long clubId,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size) {
        return managers.list(actor.userId(), clubId, page, size);
    }

    @PutMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void grant(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable("clubId") @Min(1) long clubId,
            @PathVariable("userId") @Min(1) long userId) {
        managers.change(actor.userId(), clubId, userId, true);
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable("clubId") @Min(1) long clubId,
            @PathVariable("userId") @Min(1) long userId) {
        managers.change(actor.userId(), clubId, userId, false);
    }
}
