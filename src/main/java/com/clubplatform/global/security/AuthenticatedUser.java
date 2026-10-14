package com.clubplatform.global.security;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

/** 세션에는 내부 ID와 서비스 역할만 저장하고 42 원본 개인정보는 보관하지 않는다. */
public record AuthenticatedUser(long userId, String role) implements OAuth2User, Serializable {
    @Override
    public Map<String, Object> getAttributes() {
        return Map.of("userId", userId);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if ("SUPER_ADMIN".equals(role)) {
            return List.of(
                    new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"),
                    new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getName() {
        return Long.toString(userId);
    }
}
