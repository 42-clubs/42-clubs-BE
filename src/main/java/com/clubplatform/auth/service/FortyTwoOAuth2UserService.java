package com.clubplatform.auth.service;

import com.clubplatform.global.security.AuthenticatedUser;
import com.clubplatform.user.dto.UserIdentity;
import com.clubplatform.user.service.UserAccountService;
import java.util.Locale;
import java.util.Map;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
public class FortyTwoOAuth2UserService extends DefaultOAuth2UserService {
    private final UserAccountService userAccountService;

    public FortyTwoOAuth2UserService(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) {
        Map<String, Object> attributes = super.loadUser(request).getAttributes();
        String email = text(attributes, "email");
        int at = email.lastIndexOf('@');
        if (at <= 0
                || email.indexOf('@') != at
                || !email.substring(at + 1)
                        .toLowerCase(Locale.ROOT)
                        .equals("student.42gyeongsan.kr")) {
            throw failure("campus_not_allowed");
        }
        try {
            long intraId = Long.parseLong(String.valueOf(attributes.get("id")));
            String login = text(attributes, "login");
            Object displayName = attributes.get("displayname");
            String name = displayName instanceof String value && !value.isBlank() ? value : login;
            UserIdentity user = userAccountService.synchronize42Profile(intraId, login, name);
            return new AuthenticatedUser(user.id(), user.role());
        } catch (IllegalArgumentException exception) {
            throw failure("invalid_42_profile");
        }
    }

    private static String text(Map<String, Object> attributes, String key) {
        Object value = attributes.get(key);
        if (!(value instanceof String text) || text.isBlank()) throw failure("invalid_42_profile");
        return text;
    }

    private static OAuth2AuthenticationException failure(String code) {
        return new OAuth2AuthenticationException(new OAuth2Error(code), "42 인증 정보를 확인해 주세요.");
    }
}
