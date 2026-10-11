package com.clubplatform.auth.security;

import com.clubplatform.global.security.AuthenticatedUser;
import com.clubplatform.user.service.UserAccountService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** 매 요청 DB 역할을 확인하여 로그인 후 운영 권한 변경도 바로 반영한다. */
public class CurrentUserFilter extends OncePerRequestFilter {
    private final UserAccountService users;

    public CurrentUserFilter(UserAccountService users) {
        this.users = users;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.getPrincipal() instanceof AuthenticatedUser principal) {
            var identity = users.findIdentity(principal.userId());
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            if (identity.isPresent()) {
                var current = identity.get();
                var user = new AuthenticatedUser(current.id(), current.role());
                context.setAuthentication(
                        new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
            } else if (request.getSession(false) != null) {
                request.getSession(false).invalidate();
            }
            // 세션이 공유하는 SecurityContext를 직접 변경하지 않는다.
            SecurityContextHolder.setContext(context);
        }
        chain.doFilter(request, response);
    }
}
