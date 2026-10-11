package com.clubplatform.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.clubplatform.support.PostgreSqlTestConfig;
import com.clubplatform.user.service.UserAccountService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;

/** 로컬 OAuth 제공자로 인가 코드 교환부터 DB·세션·CSRF·권한까지 검증한다. */
@SpringBootTest(
        properties = {
            "app.auth.enabled=true",
            "app.auth.client-id=test-client",
            "app.auth.client-secret=test-secret"
        })
@AutoConfigureMockMvc
@Import({PostgreSqlTestConfig.class, AuthIntegrationTests.ProviderConfig.class})
class AuthIntegrationTests {
    private static final HttpServer PROVIDER = provider();
    private static volatile Map<String, Object> profile;
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private UserAccountService accounts;

    @BeforeEach
    void prepare() {
        jdbc.update("DELETE FROM users");
        profile =
                Map.of(
                        "id",
                        4242,
                        "login",
                        "testlogin",
                        "displayname",
                        "Test User",
                        "email",
                        "testlogin@42gyeongsan.kr");
    }

    @AfterAll
    static void closeProvider() {
        PROVIDER.stop(0);
    }

    @Test
    void anonymousMeReturnsJson401AndPublicClubsRemainAccessible() throws Exception {
        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/v1/clubs")).andExpect(status().isOk());
    }

    @Test
    void loginCreatesLocalAccountAndSessionWithoutProviderProfileOrToken() throws Exception {
        var session = login();
        mvc.perform(get("/api/v1/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intraLogin").value("testlogin"))
                .andExpect(jsonPath("$.displayName").value("Test User"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.email").doesNotExist());
        var context =
                (org.springframework.security.core.context.SecurityContext)
                        session.getAttribute("SPRING_SECURITY_CONTEXT");
        var principal =
                (com.clubplatform.global.security.AuthenticatedUser)
                        context.getAuthentication().getPrincipal();
        assertThat(principal.getAttributes()).containsOnlyKeys("userId");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class)).isEqualTo(1);
    }

    @Test
    void repeatLoginUpdatesNamesAndPreservesAdminRoleAndId() throws Exception {
        login();
        long originalId = jdbc.queryForObject("SELECT id FROM users", Long.class);
        jdbc.update("UPDATE users SET role='ADMIN'");
        profile =
                Map.of(
                        "id",
                        4242,
                        "login",
                        "newlogin",
                        "displayname",
                        "New Name",
                        "email",
                        "newlogin@42gyeongsan.kr");
        mvc.perform(get("/api/v1/me").session(login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(originalId))
                .andExpect(jsonPath("$.intraLogin").value("newlogin"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class)).isEqualTo(1);
    }

    @Test
    void rejectsOtherCampusesAndSuffixSpoofingBeforeCreatingAccount() throws Exception {
        for (String email :
                List.of(
                        "user@42seoul.kr",
                        "user@evil42gyeongsan.kr",
                        "user@42gyeongsan.kr.evil",
                        "a@b@42gyeongsan.kr")) {
            profile = Map.of("id", 4242, "login", "testlogin", "email", email);
            var start = startLogin();
            mvc.perform(
                            get("/login/oauth2/code/42")
                                    .session(start.session())
                                    .param("code", "test-code")
                                    .param("state", start.state()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("OAUTH_LOGIN_FAILED"));
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class)).isZero();
    }

    @Test
    void malformedProviderProfileFailsWithoutCreatingAccount() throws Exception {
        profile = Map.of("id", "bad-id", "login", "testlogin", "email", "test@42gyeongsan.kr");
        var start = startLogin();
        mvc.perform(
                        get("/login/oauth2/code/42")
                                .session(start.session())
                                .param("code", "test-code")
                                .param("state", start.state()))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class)).isZero();
    }

    @Test
    void acceptsExactCampusDomainIgnoringCaseAndFallsBackToLoginName() throws Exception {
        profile = Map.of("id", 4242, "login", "testlogin", "email", "test@42GYEONGSAN.KR");
        mvc.perform(get("/api/v1/me").session(login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("testlogin"));
    }

    @Test
    void failedCodeExchangeDoesNotCreateAnAccount() throws Exception {
        var start = startLogin();
        mvc.perform(
                        get("/login/oauth2/code/42")
                                .session(start.session())
                                .param("code", "invalid-code")
                                .param("state", start.state()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("OAUTH_LOGIN_FAILED"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class)).isZero();
    }

    @Test
    void rejectsWrongOAuthStateAndMissingAuthorizationRequest() throws Exception {
        var start = startLogin();
        mvc.perform(
                        get("/login/oauth2/code/42")
                                .session(start.session())
                                .param("code", "test-code")
                                .param("state", "wrong-state"))
                .andExpect(status().isUnauthorized());
        mvc.perform(
                        get("/login/oauth2/code/42")
                                .param("code", "test-code")
                                .param("state", "unknown"))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class)).isZero();
    }

    @Test
    void logoutRequiresCsrfAndInvalidatesTheSession() throws Exception {
        var session = login();
        mvc.perform(post("/api/v1/auth/logout").session(session)).andExpect(status().isForbidden());
        var csrf =
                mvc.perform(get("/api/v1/auth/csrf").session(session))
                        .andExpect(status().isOk())
                        .andReturn();
        var body = mapper.readTree(csrf.getResponse().getContentAsString());
        mvc.perform(
                        post("/api/v1/auth/logout")
                                .session(session)
                                .header(
                                        body.get("headerName").asText(),
                                        body.get("token").asText()))
                .andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void loginRotatesSessionIdAndInvalidatesPreLoginCsrfToken() throws Exception {
        var start = startLogin();
        String before = start.session().getId();
        var csrf =
                mapper.readTree(
                        mvc.perform(get("/api/v1/auth/csrf").session(start.session()))
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        mvc.perform(
                        get("/login/oauth2/code/42")
                                .session(start.session())
                                .param("code", "test-code")
                                .param("state", start.state()))
                .andExpect(status().is3xxRedirection());
        assertThat(start.session().getId()).isNotEqualTo(before);
        mvc.perform(
                        post("/api/v1/auth/logout")
                                .session(start.session())
                                .header(
                                        csrf.get("headerName").asText(),
                                        csrf.get("token").asText()))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminAuthorizationUsesCurrentDatabaseRole() throws Exception {
        var session = login();
        mvc.perform(get("/api/v1/admin/not-implemented").session(session))
                .andExpect(status().isForbidden());
        jdbc.update("UPDATE users SET role='ADMIN'");
        mvc.perform(get("/api/v1/admin/not-implemented").session(session))
                .andExpect(status().isNotFound());
        jdbc.update("UPDATE users SET role='USER'");
        mvc.perform(get("/api/v1/admin/not-implemented").session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingUserInvalidatesExistingSession() throws Exception {
        var session = login();
        jdbc.update("DELETE FROM users");
        mvc.perform(get("/api/v1/me").session(session)).andExpect(status().isUnauthorized());
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void corsOnlyAllowsConfiguredFrontendWithCredentials() throws Exception {
        mvc.perform(
                        options("/api/v1/me")
                                .header("Origin", "http://localhost:5173")
                                .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        mvc.perform(
                        options("/api/v1/me")
                                .header("Origin", "https://evil.example")
                                .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    void concurrentFirstLoginsCreateOneAccount() {
        var barrier = new CyclicBarrier(2);
        var tasks =
                java.util.stream.IntStream.range(0, 2)
                        .mapToObj(
                                index ->
                                        CompletableFuture.supplyAsync(
                                                () -> {
                                                    try {
                                                        barrier.await();
                                                    } catch (Exception e) {
                                                        throw new RuntimeException(e);
                                                    }
                                                    return accounts.synchronize42Profile(
                                                                    42L, "concurrent", "Concurrent")
                                                            .id();
                                                }))
                        .toList();
        assertThat(tasks.get(0).join()).isEqualTo(tasks.get(1).join());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class)).isEqualTo(1);
    }

    private LoginStart startLogin() throws Exception {
        var result =
                mvc.perform(get("/oauth2/authorization/42"))
                        .andExpect(status().is3xxRedirection())
                        .andReturn();
        String state =
                UriComponentsBuilder.fromUriString(result.getResponse().getRedirectedUrl())
                        .build()
                        .getQueryParams()
                        .getFirst("state");
        return new LoginStart(
                (MockHttpSession) result.getRequest().getSession(false),
                java.net.URLDecoder.decode(state, StandardCharsets.UTF_8));
    }

    private MockHttpSession login() throws Exception {
        var start = startLogin();
        var result =
                mvc.perform(
                                get("/login/oauth2/code/42")
                                        .session(start.session())
                                        .param("code", "test-code")
                                        .param("state", start.state()))
                        .andExpect(status().is3xxRedirection())
                        .andExpect(redirectedUrl("http://localhost:5173/auth/callback"))
                        .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private record LoginStart(MockHttpSession session, String state) {}

    private static HttpServer provider() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext(
                    "/token",
                    exchange -> {
                        String form =
                                new String(
                                        exchange.getRequestBody().readAllBytes(),
                                        StandardCharsets.UTF_8);
                        boolean rejected = form.contains("code=invalid-code");
                        byte[] body =
                                (rejected
                                                ? "{\"error\":\"invalid_grant\"}"
                                                : "{\"access_token\":\"provider-token\",\"token_type\":\"Bearer\",\"expires_in\":3600}")
                                        .getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().set("Content-Type", "application/json");
                        exchange.sendResponseHeaders(rejected ? 400 : 200, body.length);
                        try (var output = exchange.getResponseBody()) {
                            output.write(body);
                        }
                    });
            server.createContext(
                    "/me",
                    exchange -> {
                        if (!"Bearer provider-token"
                                .equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
                            exchange.sendResponseHeaders(401, -1);
                            exchange.close();
                            return;
                        }
                        byte[] body = new ObjectMapper().writeValueAsBytes(profile);
                        exchange.getResponseHeaders().set("Content-Type", "application/json");
                        exchange.sendResponseHeaders(200, body.length);
                        try (var output = exchange.getResponseBody()) {
                            output.write(body);
                        }
                    });
            server.start();
            return server;
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProviderConfig {
        @Bean
        @Primary
        ClientRegistrationRepository testProvider() {
            String base = "http://127.0.0.1:" + PROVIDER.getAddress().getPort();
            return new InMemoryClientRegistrationRepository(
                    ClientRegistration.withRegistrationId("42")
                            .clientId("test-client")
                            .clientSecret("test-secret")
                            .clientAuthenticationMethod(
                                    ClientAuthenticationMethod.CLIENT_SECRET_POST)
                            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                            .redirectUri("http://localhost/login/oauth2/code/42")
                            .scope("public")
                            .authorizationUri(base + "/authorize")
                            .tokenUri(base + "/token")
                            .userInfoUri(base + "/me")
                            .userNameAttributeName("id")
                            .clientName("Test 42")
                            .build());
        }
    }
}
