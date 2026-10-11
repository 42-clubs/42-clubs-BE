package com.clubplatform.user.controller;

import com.clubplatform.global.security.AuthenticatedUser;
import com.clubplatform.user.dto.MeResponse;
import com.clubplatform.user.service.UserAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MeController {
    private final UserAccountService userAccountService;

    @GetMapping("/api/v1/me")
    public MeResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return userAccountService.findMe(user.userId());
    }
}
