package com.clubplatform.verification;

import static org.assertj.core.api.Assertions.assertThat;

import com.clubplatform.support.PostgreSqlTestConfig;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Opt-in real HTTP verification using FE API functions and an isolated PostgreSQL database. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = {
            "app.auth.enabled=true",
            "app.auth.client-id=fixture-client",
            "app.auth.client-secret=fixture-secret",
            "app.auth.success-url=http://localhost:5176/#/auth/callback",
            "app.auth.allowed-origins=http://localhost:5176"
        })
@Import({PostgreSqlTestConfig.class, FrontendLiveIntegrationTests.ProviderConfig.class})
class FrontendLiveIntegrationTests {
    private static final HttpServer PROVIDER = provider();
    private static final Path PHOTOS = temporaryPhotos();
    private static final int PORT = availablePort();
    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.photos.mode", () -> "local");
        registry.add("app.photos.local-directory", PHOTOS::toString);
        registry.add("server.port", () -> PORT);
        registry.add("app.photos.local-base-url", () -> "http://127.0.0.1:" + PORT);
    }

    @AfterAll
    static void cleanup() throws Exception {
        PROVIDER.stop(0);
        try (var paths = Files.walk(PHOTOS)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    @Test
    void frontendApiFunctionsUseRealHttpSessionsCsrfAndDatabase() throws Exception {
        String frontend = System.getProperty("fe.repo.path");
        assertThat(frontend).as("Set -PfeRepoPath to the installed FE repository").isNotBlank();
        String[] names = {"super", "admin", "manager", "member", "outsider"};
        String[] roles = {"SUPER_ADMIN", "ADMIN", "USER", "USER", "USER"};
        for (int i = 0; i < names.length; i++) {
            jdbc.update(
                    "INSERT INTO users (id,intra_id,intra_login,display_name,role,created_at,updated_at)"
                            + " VALUES (?,?,?,?,?,now(),now())",
                    i + 1,
                    9001 + i,
                    names[i],
                    names[i],
                    roles[i]);
        }
        // More than FE readAllPages' size=100 to exercise actual subsequent-page requests.
        for (int i = 1; i <= 106; i++) {
            jdbc.update(
                    "INSERT INTO clubs (id,name,summary,description,operation_status,created_at,updated_at)"
                            + " VALUES (?,?,?,?,?,now(),now())",
                    i,
                    "Live club " + i,
                    "Live summary",
                    "Live description",
                    i == 106 ? "ARCHIVED" : "ACTIVE");
        }
        var process =
                new ProcessBuilder(
                                "node",
                                "scripts/verification/fe-live.mjs",
                                frontend,
                                "http://127.0.0.1:" + port)
                        .inheritIO()
                        .start();
        try {
            assertThat(process.waitFor(120, TimeUnit.SECONDS)).as("FE harness timeout").isTrue();
            assertThat(process.exitValue()).as("FE live assertions").isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM activities", Long.class))
                    .isGreaterThanOrEqualTo(1L);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM activity_images", Long.class))
                    .isEqualTo(1L);
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    private static Path temporaryPhotos() {
        try {
            return Files.createTempDirectory("clubs-fe-live-photos-");
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static int availablePort() {
        try (var socket =
                new java.net.ServerSocket(0, 0, java.net.InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static HttpServer provider() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext(
                    "/token",
                    exchange -> {
                        String form =
                                new String(
                                        exchange.getRequestBody().readAllBytes(),
                                        StandardCharsets.UTF_8);
                        var match =
                                java.util.regex.Pattern.compile("(?:^|&)code=(900[1-5])(?:&|$)")
                                        .matcher(form);
                        boolean valid = match.find();
                        String body =
                                valid
                                        ? "{\"access_token\":\""
                                                + match.group(1)
                                                + "\",\"token_type\":\"Bearer\"}"
                                        : "{\"error\":\"invalid_grant\"}";
                        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().set("Content-Type", "application/json");
                        exchange.sendResponseHeaders(valid ? 200 : 400, bytes.length);
                        try (var output = exchange.getResponseBody()) {
                            output.write(bytes);
                        }
                    });
            server.createContext(
                    "/me",
                    exchange -> {
                        String token = exchange.getRequestHeaders().getFirst("Authorization");
                        if (token == null || !token.matches("Bearer 900[1-5]")) {
                            exchange.sendResponseHeaders(401, -1);
                            exchange.close();
                            return;
                        }
                        int id = Integer.parseInt(token.substring(7));
                        String name =
                                new String[] {"super", "admin", "manager", "member", "outsider"}
                                        [id - 9001];
                        String body =
                                "{\"id\":"
                                        + id
                                        + ",\"login\":\""
                                        + name
                                        + "\",\"displayname\":\""
                                        + name
                                        + "\",\"email\":\""
                                        + name
                                        + "@student.42gyeongsan.kr\"}";
                        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().set("Content-Type", "application/json");
                        exchange.sendResponseHeaders(200, bytes.length);
                        try (var output = exchange.getResponseBody()) {
                            output.write(bytes);
                        }
                    });
            server.start();
            return server;
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProviderConfig {
        @Bean
        @Primary
        ClientRegistrationRepository localProvider() {
            String base = "http://127.0.0.1:" + PROVIDER.getAddress().getPort();
            return new InMemoryClientRegistrationRepository(
                    ClientRegistration.withRegistrationId("42")
                            .clientId("fixture-client")
                            .clientSecret("fixture-secret")
                            .clientAuthenticationMethod(
                                    ClientAuthenticationMethod.CLIENT_SECRET_POST)
                            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                            .scope("public")
                            .authorizationUri(base + "/authorize")
                            .tokenUri(base + "/token")
                            .userInfoUri(base + "/me")
                            .userNameAttributeName("id")
                            .clientName("Local fixture, not real 42")
                            .build());
        }
    }
}
