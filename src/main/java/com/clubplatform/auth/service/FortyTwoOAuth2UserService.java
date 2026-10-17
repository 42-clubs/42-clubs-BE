package com.clubplatform.auth.service;

import com.clubplatform.global.security.AuthenticatedUser;
import com.clubplatform.user.dto.UserIdentity;
import com.clubplatform.user.service.UserAccountService;
import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
public class FortyTwoOAuth2UserService extends DefaultOAuth2UserService {
    private static final Set<String> ALLOWED_EMAIL_DOMAINS =
            Set.of("student.42gyeongsan.kr", "42gyeongsan.kr");
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
                || !ALLOWED_EMAIL_DOMAINS.contains(
                        email.substring(at + 1).toLowerCase(Locale.ROOT))) {
            throw failure("campus_not_allowed");
        }
        try {
            long intraId = Long.parseLong(String.valueOf(attributes.get("id")));
            String login = text(attributes, "login");
            Object displayName = attributes.get("displayname");
            String name = displayName instanceof String value && !value.isBlank() ? value : login;
            UserIdentity user =
                    userAccountService.synchronize42Profile(
                            intraId, login, name, mediumProfileImageUrl(attributes));
            return new AuthenticatedUser(user.id(), user.role());
        } catch (IllegalArgumentException exception) {
            throw failure("invalid_42_profile");
        }
    }

    /** 선택 정보인 이미지가 누락되거나 잘못되어도 로그인은 허용한다. */
    private static String mediumProfileImageUrl(Map<String, Object> attributes) {
        if (!(attributes.get("image") instanceof Map<?, ?> image)
                || !(image.get("versions") instanceof Map<?, ?> versions)
                || !(versions.get("medium") instanceof String url)
                || url.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(url);
            return "https".equalsIgnoreCase(uri.getScheme())
                            && uri.getHost() != null
                            && uri.getRawUserInfo() == null
                    ? url
                    : null;
        } catch (IllegalArgumentException exception) {
            return null;
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
