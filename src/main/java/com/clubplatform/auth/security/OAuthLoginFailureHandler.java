package com.clubplatform.auth.security;

import com.clubplatform.global.exception.ApiErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuthLoginFailureHandler implements AuthenticationFailureHandler {
    private static final Map<String, String> REASONS =
            Map.ofEntries(
                    Map.entry(
                            "campus_not_allowed",
                            "42 이메일 도메인이 student.42gyeongsan.kr 또는 42gyeongsan.kr인지 확인하세요."),
                    Map.entry("invalid_42_profile", "42 응답의 이메일·ID·로그인명 등 필수 프로필을 확인하세요."),
                    Map.entry(
                            "authorization_request_not_found",
                            "state에 일치하는 로그인 시작 요청이 없습니다. 쿠키·호스트·state·서버 재시작 여부를 확인하세요."),
                    Map.entry(
                            "invalid_state_parameter",
                            "OAuth state가 일치하지 않습니다. 로그인 시작 주소에서 다시 시도하세요."),
                    Map.entry("invalid_client", "같은 42 앱의 UID·Secret인지 확인하세요."),
                    Map.entry("invalid_grant", "인가 코드 만료·재사용 또는 콜백 URI 불일치를 확인하세요."),
                    Map.entry(
                            "invalid_token_response",
                            "42 토큰 교환에 실패했습니다. 자격증명·콜백 URI·42 서버 연결을 확인하세요."),
                    Map.entry(
                            "invalid_user_info_response", "42 프로필 조회에 실패했습니다. 42 서버 연결·응답을 확인하세요."),
                    Map.entry("access_denied", "42 인가가 거부되거나 취소됐습니다."),
                    Map.entry("invalid_request", "OAuth 인가 요청의 필수 매개변수를 확인하세요."),
                    Map.entry("invalid_scope", "42 앱에서 허용한 OAuth scope를 확인하세요."),
                    Map.entry("server_error", "42 인증 서버 오류입니다. 잠시 후 다시 시도하세요."),
                    Map.entry("temporarily_unavailable", "42 인증 서버가 일시적으로 사용 불가합니다."));
    private final ObjectMapper mapper;

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception)
            throws IOException {
        String reason = "unknown_oauth_error";
        // 제공자 오류 코드·메시지는 외부 입력이다. 알려진 코드만 기록하고 원문·예외는 출력하지 않는다.
        // 토큰 교환 예외 내부에 invalid_client/invalid_grant가 있으면 더 구체적인 원인을 사용한다.
        Throwable cause = exception;
        for (int depth = 0; cause != null && depth < 16; depth++, cause = cause.getCause()) {
            if (cause instanceof OAuth2AuthenticationException oauth
                    && REASONS.containsKey(oauth.getError().getErrorCode())) {
                reason = oauth.getError().getErrorCode();
            }
        }
        log.warn(
                "42 OAuth login failed: reason={} hint={}",
                reason,
                REASONS.getOrDefault(reason, "분류되지 않은 인증 오류입니다. 서버 설정과 42 연동을 확인하세요."));
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        mapper.writeValue(
                response.getOutputStream(),
                new ApiErrorResponse("OAUTH_LOGIN_FAILED", "42경산 계정으로 다시 로그인해 주세요."));
    }
}
