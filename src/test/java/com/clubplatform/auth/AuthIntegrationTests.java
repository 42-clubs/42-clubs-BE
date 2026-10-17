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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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
@ExtendWith(OutputCaptureExtension.class)
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
        jdbc.update("DELETE FROM club_managers");
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
                        "testlogin@student.42gyeongsan.kr");
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
                        "newlogin@student.42gyeongsan.kr");
        mvc.perform(get("/api/v1/me").session(login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(originalId))
                .andExpect(jsonPath("$.intraLogin").value("newlogin"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class)).isEqualTo(1);
    }

    @Test
    void mediumImageIsStoredReturnedAndUpdatedWithoutChangingIdentityOrRole() throws Exception {
        String originalUrl = "https://images.example.test/medium/avatar.jpg?version=1";
        profileWithImage(
                Map.of(
                        "versions",
                        Map.of(
                                "medium", originalUrl,
                                "large", "https://images.example.test/large/avatar.jpg",
                                "small", "https://images.example.test/small/avatar.jpg")));
        var firstSession = login();
        long originalId = jdbc.queryForObject("SELECT id FROM users", Long.class);
        var createdAt =
                jdbc.queryForObject("SELECT created_at FROM users", java.time.OffsetDateTime.class);
        mvc.perform(get("/api/v1/me").session(firstSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileImageUrl").value(originalUrl));
        assertThat(jdbc.queryForObject("SELECT profile_image_url FROM users", String.class))
                .isEqualTo(originalUrl);
        jdbc.update("UPDATE users SET role='ADMIN'");
        String updatedUrl = "https://images.example.test/medium/new-avatar.jpg";
        profileWithImage(Map.of("versions", Map.of("medium", updatedUrl)));
        mvc.perform(get("/api/v1/me").session(login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(originalId))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.profileImageUrl").value(updatedUrl));
        assertThat(jdbc.queryForObject("SELECT profile_image_url FROM users", String.class))
                .isEqualTo(updatedUrl);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT created_at FROM users", java.time.OffsetDateTime.class))
                .isEqualTo(createdAt);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class)).isEqualTo(1);
        // 기존 세션도 최신 DB 값을 반환하며 42 API를 다시 조회하지 않는다.
        profile = Map.of();
        mvc.perform(get("/api/v1/me").session(firstSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileImageUrl").value(updatedUrl));
    }

    @ParameterizedTest
    @MethodSource("unavailableImages")
    void unavailableImageAllowsLoginAndClearsPreviouslyStoredUrl(Object image) throws Exception {
        profileWithImage(image);
        var session = login();
        assertNullProfileImage(session);
        jdbc.update("UPDATE users SET profile_image_url='https://images.example.test/old.jpg'");
        assertNullProfileImage(login());
    }

    static java.util.stream.Stream<Object> unavailableImages() {
        return java.util.stream.Stream.of(
                null,
                "invalid-image",
                Map.of(),
                Map.of("versions", "invalid-versions"),
                Map.of("versions", Map.of("large", "https://images.example.test/large.jpg")),
                Map.of("versions", Map.of("medium", 42)),
                Map.of("versions", Map.of("medium", "  ")),
                Map.of("versions", Map.of("medium", "not a URL")),
                Map.of("versions", Map.of("medium", "/relative.jpg")),
                Map.of("versions", Map.of("medium", "http://images.example.test/avatar.jpg")),
                Map.of("versions", Map.of("medium", "javascript:alert(1)")),
                Map.of(
                        "versions",
                        Map.of("medium", "https://user:password@images.example.test/avatar.jpg")));
    }

    @Test
    void existingAccountHasNullImageUntilItsNextLogin() throws Exception {
        jdbc.update(
                """
                INSERT INTO users (intra_id, intra_login, display_name, role, created_at, updated_at)
                VALUES (4242, 'before-login', 'Initial User', 'ADMIN', now(), now())
                """);
        long originalId = jdbc.queryForObject("SELECT id FROM users", Long.class);
        assertThat(jdbc.queryForObject("SELECT profile_image_url FROM users", String.class))
                .isNull();
        String url = "https://images.example.test/medium/avatar.jpg";
        profileWithImage(Map.of("versions", Map.of("medium", url)));
        mvc.perform(get("/api/v1/me").session(login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(originalId))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.profileImageUrl").value(url));
    }

    private void profileWithImage(Object image) {
        var updated = new HashMap<>(profile);
        updated.put("image", image);
        profile = updated;
    }

    private void assertNullProfileImage(MockHttpSession session) throws Exception {
        var response =
                mvc.perform(get("/api/v1/me").session(session))
                        .andExpect(status().isOk())
                        .andReturn();
        var body = mapper.readTree(response.getResponse().getContentAsString());
        assertThat(body.has("profileImageUrl")).isTrue();
        assertThat(body.get("profileImageUrl").isNull()).isTrue();
        assertThat(jdbc.queryForObject("SELECT profile_image_url FROM users", String.class))
                .isNull();
    }

    @Test
    void missingImageAllowsLoginAndClearsPreviouslyStoredUrl() throws Exception {
        var session = login();
        assertNullProfileImage(session);
        jdbc.update("UPDATE users SET profile_image_url='https://images.example.test/old.jpg'");
        assertNullProfileImage(login());
    }

    @Test
    void rejectsOtherCampusesAndSuffixSpoofingBeforeCreatingAccount() throws Exception {
        for (String email :
                List.of(
                        "user@42seoul.kr",
                        "user@evil42gyeongsan.kr",
                        "user@42gyeongsan.kr.evil",
                        "user@sub.42gyeongsan.kr",
                        "a@b@42gyeongsan.kr",
                        "user@evilstudent.42gyeongsan.kr",
                        "user@sub.student.42gyeongsan.kr",
                        "user@student.42gyeongsan.kr.evil",
                        "a@b@student.42gyeongsan.kr")) {
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
        profile =
                Map.of(
                        "id",
                        "bad-id",
                        "login",
                        "testlogin",
                        "email",
                        "test@student.42gyeongsan.kr");
        var start = startLogin();
        mvc.perform(
                        get("/login/oauth2/code/42")
                                .session(start.session())
                                .param("code", "test-code")
                                .param("state", start.state()))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class)).isZero();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "test@student.42gyeongsan.kr", "test@STUDENT.42GYEONGSAN.KR",
                "test@42gyeongsan.kr", "test@42GYEONGSAN.KR"
            })
    void acceptsStudentAndStaffDomainsIgnoringCaseWithoutGrantingAdmin(String email)
            throws Exception {
        profile = Map.of("id", 4242, "login", "testlogin", "email", email);
        mvc.perform(get("/api/v1/me").session(login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("testlogin"))
                .andExpect(jsonPath("$.role").value("USER"));
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
    void rejectsWrongOAuthStateAndMissingAuthorizationRequest(CapturedOutput output)
            throws Exception {
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
        // 기본 세션 저장소는 state가 다른 요청도 일치하는 인가 요청 없음으로 처리한다.
        assertThat(output.getOut()).contains("reason=authorization_request_not_found");
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
    void oauthProfileCannotGrantAdminToNewAccount() throws Exception {
        profile =
                Map.of(
                        "id",
                        4242,
                        "login",
                        "testlogin",
                        "displayname",
                        "Test User",
                        "email",
                        "testlogin@student.42gyeongsan.kr",
                        "role",
                        "ADMIN");
        var session = login();
        mvc.perform(get("/api/v1/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("USER"));
        mvc.perform(get("/api/v1/admin/test-operation").session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void initialAdminLoginPreservesAccountAndRoleAndRevocationAffectsSession() throws Exception {
        jdbc.update(
                """
                INSERT INTO users (intra_id, intra_login, display_name, role, created_at, updated_at)
                VALUES (4242, 'before-login', 'Initial User', 'ADMIN', now(), now())
                """);
        long initialId = jdbc.queryForObject("SELECT id FROM users", Long.class);
        var createdAt =
                jdbc.queryForObject("SELECT created_at FROM users", java.time.OffsetDateTime.class);
        var session = login();
        mvc.perform(get("/api/v1/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(initialId))
                .andExpect(jsonPath("$.intraLogin").value("testlogin"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
        var context =
                (org.springframework.security.core.context.SecurityContext)
                        session.getAttribute("SPRING_SECURITY_CONTEXT");
        assertThat(context.getAuthentication().getAuthorities())
                .extracting(org.springframework.security.core.GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");
        mvc.perform(get("/api/v1/admin/test-operation").session(session))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/test-operation").session(session))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post("/api/v1/admin/test-operation")
                                .session(session)
                                .with(
                                        org.springframework.security.test.web.servlet.request
                                                .SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class)).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT created_at FROM users", java.time.OffsetDateTime.class))
                .isEqualTo(createdAt);
        jdbc.update("UPDATE users SET role='USER'");
        mvc.perform(get("/api/v1/admin/test-operation").session(session))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/me").session(session)).andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void managerRevocationAffectsExistingSessionAndAdminCannotBypassRelation() throws Exception {
        var session = login();
        long userId = jdbc.queryForObject("SELECT id FROM users", Long.class);
        long clubId =
                jdbc.queryForObject(
                        """
                INSERT INTO clubs (name, summary, description, operation_status,
                    recruitment_planned, recruitment_closed_early, created_at, updated_at)
                VALUES ('Permission Test', 'Summary', 'Description', 'ACTIVE', false, false, now(), now())
                RETURNING id
                """,
                        Long.class);
        jdbc.update("UPDATE users SET role='ADMIN'");
        mvc.perform(
                        post("/api/v1/test-clubs/{clubId}/write", clubId)
                                .session(session)
                                .with(
                                        org.springframework.security.test.web.servlet.request
                                                .SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        jdbc.update(
                "INSERT INTO club_managers (user_id, club_id, created_at) VALUES (?, ?, now())",
                userId,
                clubId);
        mvc.perform(
                        post("/api/v1/test-clubs/{clubId}/write", clubId)
                                .session(session)
                                .with(
                                        org.springframework.security.test.web.servlet.request
                                                .SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk());
        jdbc.update("DELETE FROM club_managers WHERE user_id=? AND club_id=?", userId, clubId);
        mvc.perform(
                        post("/api/v1/test-clubs/{clubId}/write", clubId)
                                .session(session)
                                .with(
                                        org.springframework.security.test.web.servlet.request
                                                .SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isForbidden());
        jdbc.update("DELETE FROM clubs WHERE id=?", clubId);
    }

    @org.springframework.web.bind.annotation.RestController
    static class TestOperationController {
        private final com.clubplatform.club.service.ClubPermissionService permissions;

        TestOperationController(com.clubplatform.club.service.ClubPermissionService permissions) {
            this.permissions = permissions;
        }

        @org.springframework.web.bind.annotation.PostMapping("/api/v1/test-clubs/{clubId}/write")
        String writeClub(
                @org.springframework.web.bind.annotation.PathVariable long clubId,
                @org.springframework.security.core.annotation.AuthenticationPrincipal
                        com.clubplatform.global.security.AuthenticatedUser actor) {
            permissions.requireManager(actor.userId(), clubId);
            return "ok";
        }

        @org.springframework.web.bind.annotation.GetMapping("/api/v1/admin/test-operation")
        String read() {
            return "ok";
        }

        @org.springframework.web.bind.annotation.PostMapping("/api/v1/admin/test-operation")
        String write() {
            return "ok";
        }
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
        TestOperationController testOperationController(
                com.clubplatform.club.service.ClubPermissionService permissions) {
            return new TestOperationController(permissions);
        }

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
