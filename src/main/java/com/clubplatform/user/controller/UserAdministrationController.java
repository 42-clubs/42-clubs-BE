package com.clubplatform.user.controller;

import com.clubplatform.global.security.AuthenticatedUser;
import com.clubplatform.user.dto.UserDirectoryPage;
import com.clubplatform.user.service.UserAdministrationService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/users")
public class UserAdministrationController {
    private final UserAdministrationService users;

    @GetMapping
    public UserDirectoryPage search(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(name = "intraLogin", required = false)
                    @Size(min = 1, max = 100)
                    @Pattern(regexp = "(?s).*\\S.*")
                    String intraLogin,
            @RequestParam(name = "role", required = false) @Pattern(regexp = "ADMIN") String role,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size) {
        return users.search(actor.userId(), intraLogin, role, page, size);
    }

    @PutMapping("/{userId}/admin-role")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void grant(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable("userId") @Min(1) long userId) {
        users.changeAdminRole(actor.userId(), userId, true);
    }

    @DeleteMapping("/{userId}/admin-role")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable("userId") @Min(1) long userId) {
        users.changeAdminRole(actor.userId(), userId, false);
    }
}
