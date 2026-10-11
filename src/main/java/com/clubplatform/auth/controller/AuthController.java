package com.clubplatform.auth.controller;

import com.clubplatform.auth.dto.CsrfResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {
    /** 로그인 전·로그인 후·로그아웃 후 각각 재조회하여 현재 세션의 토큰을 사용한다. */
    @GetMapping("/api/v1/auth/csrf")
    public CsrfResponse csrf(CsrfToken csrfToken) {
        return new CsrfResponse(csrfToken.getHeaderName(), csrfToken.getToken());
    }
}
