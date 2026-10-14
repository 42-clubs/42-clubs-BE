package com.clubplatform.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

@ExtendWith(OutputCaptureExtension.class)
class OAuthLoginFailureHandlerTests {
    private final ObjectMapper mapper = new ObjectMapper();
    private final OAuthLoginFailureHandler handler = new OAuthLoginFailureHandler(mapper);

    @ParameterizedTest
    @ValueSource(
            strings = {
                "campus_not_allowed",
                "invalid_42_profile",
                "authorization_request_not_found",
                "invalid_state_parameter",
                "invalid_client",
                "invalid_grant",
                "invalid_token_response",
                "invalid_user_info_response",
                "access_denied"
            })
    void logsDistinctReasonsWithoutExposingSensitiveDetails(String reason, CapturedOutput output)
            throws Exception {
        var request = new MockHttpServletRequest();
        request.setQueryString("code=private-code&state=private-state");
        var response = new MockHttpServletResponse();
        handler.onAuthenticationFailure(
                request,
                response,
                new OAuth2AuthenticationException(
                        new OAuth2Error(reason, "private-token private-email", "private-uri"),
                        "private-secret"));
        assertThat(output.getOut()).contains("reason=" + reason, "hint=");
        assertThat(output.getAll()).doesNotContain("private-");
        assertThat(response.getStatus()).isEqualTo(401);
        var body = mapper.readTree(response.getContentAsString());
        assertThat(body.get("code").asText()).isEqualTo("OAUTH_LOGIN_FAILED");
        assertThat(body.get("message").asText()).isEqualTo("42경산 계정으로 다시 로그인해 주세요.");
        assertThat(response.getContentAsString()).doesNotContain(reason, "private-");
    }

    @Test
    void nestedProviderErrorIdentifiesInvalidCredentials(CapturedOutput output) throws Exception {
        var cause = new OAuth2AuthenticationException(new OAuth2Error("invalid_client"));
        handler.onAuthenticationFailure(
                new MockHttpServletRequest(),
                new MockHttpServletResponse(),
                new OAuth2AuthenticationException(
                        new OAuth2Error("invalid_token_response"), "private-secret", cause));
        assertThat(output.getOut()).contains("reason=invalid_client");
        assertThat(output.getAll()).doesNotContain("private-secret");
    }

    @Test
    void unknownProviderErrorCannotInjectLogsOrExposeSecrets(CapturedOutput output)
            throws Exception {
        handler.onAuthenticationFailure(
                new MockHttpServletRequest(),
                new MockHttpServletResponse(),
                new OAuth2AuthenticationException(new OAuth2Error("private-secret\nforged-log")));
        assertThat(output.getOut()).contains("reason=unknown_oauth_error");
        assertThat(output.getAll()).doesNotContain("private-secret", "forged-log");
    }
}
