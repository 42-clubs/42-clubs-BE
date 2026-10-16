package com.clubplatform.notice;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.clubplatform.club.entity.Club;
import com.clubplatform.club.repository.ClubRepository;
import com.clubplatform.club.service.ClubMembershipService;
import com.clubplatform.global.security.AuthenticatedUser;
import com.clubplatform.notice.entity.*;
import com.clubplatform.notice.repository.NoticeRepository;
import com.clubplatform.support.PostgreSqlTestConfig;
import com.clubplatform.user.service.UserAccountService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgreSqlTestConfig.class)
class NoticeAccessIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserAccountService accounts;
    @Autowired ClubRepository clubs;
    @Autowired ClubMembershipService membership;
    @Autowired NoticeRepository notices;
    @Autowired ObjectMapper mapper;
    Map<String, Long> users = new HashMap<>();
    long club, otherClub, privateId, publicId;

    @BeforeEach
    void setup() {
        clean();
        jdbc.update("DELETE FROM club_managers");
        jdbc.update("DELETE FROM clubs");
        jdbc.update("DELETE FROM users");
        long intra = 8000;
        for (String role :
                List.of(
                        "OUTSIDER",
                        "LEFT",
                        "OTHER_MANAGER",
                        "MEMBER",
                        "MANAGER",
                        "ADMIN",
                        "SUPER_ADMIN"))
            users.put(role, accounts.synchronize42Profile(++intra, role.toLowerCase(), role).id());
        for (String role : List.of("ADMIN", "SUPER_ADMIN"))
            jdbc.update("UPDATE users SET role=? WHERE id=?", role, users.get(role));
        club = clubs.saveAndFlush(new Club("A", "Summary", "Description")).getId();
        otherClub = clubs.saveAndFlush(new Club("B", "Summary", "Description")).getId();
        jdbc.update(
                "INSERT INTO club_managers(user_id,club_id,created_at) VALUES (?,?,now())",
                users.get("MANAGER"),
                club);
        jdbc.update(
                "INSERT INTO club_managers(user_id,club_id,created_at) VALUES (?,?,now())",
                users.get("OTHER_MANAGER"),
                otherClub);
        membership.register(users.get("ADMIN"), club, users.get("MEMBER"));
        membership.register(users.get("ADMIN"), club, users.get("LEFT"));
        jdbc.update(
                "UPDATE club_members SET status='LEFT',left_at=joined_at WHERE user_id=?",
                users.get("LEFT"));
        Instant time = Instant.parse("2026-01-01T00:00:00Z");
        privateId =
                notices.saveAndFlush(
                                new Notice(
                                        club,
                                        users.get("MANAGER"),
                                        "Private old",
                                        "Secret",
                                        NoticeVisibility.MEMBERS_ONLY,
                                        time))
                        .getId();
        publicId =
                notices.saveAndFlush(
                                new Notice(
                                        club,
                                        users.get("MANAGER"),
                                        "Public old",
                                        "Public",
                                        NoticeVisibility.PUBLIC,
                                        time.plusSeconds(1)))
                        .getId();
        notices.saveAndFlush(
                new Notice(
                        club,
                        users.get("MANAGER"),
                        "Private new",
                        "Secret",
                        NoticeVisibility.MEMBERS_ONLY,
                        time.plusSeconds(2)));
        notices.saveAndFlush(
                new Notice(
                        club,
                        users.get("MANAGER"),
                        "Public new",
                        "Public",
                        NoticeVisibility.PUBLIC,
                        time.plusSeconds(3)));
    }

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM announcements");
        jdbc.update("DELETE FROM notices");
        jdbc.update("DELETE FROM club_members");
    }

    MockHttpSession session(long userId) {
        var identity = accounts.findIdentity(userId).orElseThrow();
        var principal = new AuthenticatedUser(identity.id(), identity.role());
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, principal.getAuthorities()));
        var session = new MockHttpSession();
        session.setAttribute("SPRING_SECURITY_CONTEXT", context);
        return session;
    }

    MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String role) {
        return "ANONYMOUS".equals(role) ? request : request.session(session(users.get(role)));
    }

    String path() {
        return "/api/v1/clubs/" + club + "/notices";
    }

    boolean privileged(String role) {
        return Set.of("MEMBER", "MANAGER", "ADMIN", "SUPER_ADMIN").contains(role);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "ANONYMOUS",
                "OUTSIDER",
                "LEFT",
                "OTHER_MANAGER",
                "MEMBER",
                "MANAGER",
                "ADMIN",
                "SUPER_ADMIN"
            })
    void listFiltersBeforePaginationAndDetailEnforcesCurrentRelationship(String role)
            throws Exception {
        int count = privileged(role) ? 4 : 2;
        mvc.perform(as(get(path()).param("size", "1"), role))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(count))
                .andExpect(jsonPath("$.totalPages").value(count))
                .andExpect(jsonPath("$.content[0].title").value("Public new"));
        mvc.perform(as(get(path()).param("size", "1").param("page", "1"), role))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.content[0].title")
                                .value(privileged(role) ? "Private new" : "Public old"));
        mvc.perform(as(get(path() + "/" + privateId), role))
                .andExpect(status().is(privileged(role) ? 200 : 404));
        mvc.perform(as(get(path() + "/" + publicId), role))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visibility").value("PUBLIC"));
        mvc.perform(as(get("/api/v1/clubs/" + otherClub + "/notices/" + publicId), role))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "OUTSIDER",
                "LEFT",
                "OTHER_MANAGER",
                "MEMBER",
                "MANAGER",
                "ADMIN",
                "SUPER_ADMIN"
            })
    void clubWritingRequiresManagerOrOperatorAndDefaultsToPrivate(String role) throws Exception {
        boolean allowed = Set.of("MANAGER", "ADMIN", "SUPER_ADMIN").contains(role);
        var result =
                mvc.perform(
                                as(
                                        post(path())
                                                .with(csrf())
                                                .contentType("application/json")
                                                .content(
                                                        "{\"title\":\"New\",\"content\":\"Body\"}"),
                                        role))
                        .andExpect(status().is(allowed ? 201 : 403))
                        .andReturn();
        if (allowed) {
            long id = mapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
            assertThat(result.getResponse().getHeader("Location")).isEqualTo(path() + "/" + id);
            assertThat(notices.findById(id).orElseThrow().getVisibility())
                    .isEqualTo(NoticeVisibility.MEMBERS_ONLY);
            mvc.perform(get(path() + "/" + id)).andExpect(status().isNotFound());
        }
    }

    @Test
    void publicSelectionNullDefaultValidationAndCsrf() throws Exception {
        long actor = users.get("MANAGER");
        mvc.perform(
                        post(path())
                                .session(session(actor))
                                .contentType("application/json")
                                .content("{\"title\":\"New\",\"content\":\"Body\"}"))
                .andExpect(status().isForbidden());
        for (String visibility : List.of("\"PUBLIC\"", "null")) {
            var result =
                    mvc.perform(
                                    post(path())
                                            .session(session(actor))
                                            .with(csrf())
                                            .contentType("application/json")
                                            .content(
                                                    "{\"title\":\"New\",\"content\":\"Body\",\"visibility\":"
                                                            + visibility
                                                            + "}"))
                            .andExpect(status().isCreated())
                            .andReturn();
            long id = mapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
            mvc.perform(get(path() + "/" + id))
                    .andExpect(status().is("null".equals(visibility) ? 404 : 200));
        }
        mvc.perform(
                        post(path())
                                .session(session(actor))
                                .with(csrf())
                                .contentType("application/json")
                                .content(
                                        "{\"title\":\"New\",\"content\":\"Body\",\"visibility\":\"INVALID\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(path()).param("size", "101")).andExpect(status().isBadRequest());
        jdbc.update("UPDATE clubs SET operation_status='ARCHIVED' WHERE id=?", club);
        for (String role : List.of("MANAGER", "ADMIN", "SUPER_ADMIN"))
            mvc.perform(
                            as(
                                    post(path())
                                            .with(csrf())
                                            .contentType("application/json")
                                            .content("{\"title\":\"New\",\"content\":\"Body\"}"),
                                    role))
                    .andExpect(status().isForbidden());
    }

    @Test
    void relationshipAndRoleRevocationAffectExistingSessionsAndAuthors() throws Exception {
        var member = session(users.get("MEMBER"));
        var manager = session(users.get("MANAGER"));
        var admin = session(users.get("ADMIN"));
        for (var s : List.of(member, manager, admin))
            mvc.perform(get(path() + "/" + privateId).session(s)).andExpect(status().isOk());
        jdbc.update(
                "UPDATE club_members SET status='LEFT',left_at=joined_at WHERE user_id=?",
                users.get("MEMBER"));
        jdbc.update("DELETE FROM club_managers WHERE user_id=?", users.get("MANAGER"));
        jdbc.update("UPDATE users SET role='USER' WHERE id=?", users.get("ADMIN"));
        for (var s : List.of(member, manager, admin))
            mvc.perform(get(path() + "/" + privateId).session(s)).andExpect(status().isNotFound());
    }

    @Test
    void deletedPrivateAndPublicNoticesAreHiddenAndLegacyPathIsRemoved() throws Exception {
        jdbc.update("UPDATE notices SET deleted_at=now() WHERE id IN (?,?)", privateId, publicId);
        mvc.perform(get(path() + "/" + privateId).session(session(users.get("SUPER_ADMIN"))))
                .andExpect(status().isNotFound());
        mvc.perform(get(path() + "/" + publicId)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/notices/" + publicId).session(session(users.get("ADMIN"))))
                .andExpect(status().isNotFound());
        mvc.perform(get(path()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"MANAGER", "ADMIN", "SUPER_ADMIN", "OUTSIDER"})
    void announcementsAreSeparatePublicAndOnlyOperatorsCanWrite(String role) throws Exception {
        boolean allowed = Set.of("ADMIN", "SUPER_ADMIN").contains(role);
        var result =
                mvc.perform(
                                as(
                                        post("/api/v1/announcements")
                                                .with(csrf())
                                                .contentType("application/json")
                                                .content(
                                                        "{\"title\":\"Registration\",\"content\":\"Dates\"}"),
                                        role))
                        .andExpect(status().is(allowed ? 201 : 403))
                        .andReturn();
        if (allowed) {
            long id = mapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
            assertThat(result.getResponse().getHeader("Location"))
                    .isEqualTo("/api/v1/announcements/" + id);
            mvc.perform(get("/api/v1/announcements/" + id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.authorId").value(users.get(role)))
                    .andExpect(jsonPath("$.clubId").doesNotExist());
        }
        mvc.perform(get("/api/v1/announcements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(allowed ? 1 : 0));
        mvc.perform(get(path()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void announcementValidationCsrfOrderingSoftDeletionAndRevocation() throws Exception {
        var admin = session(users.get("ADMIN"));
        String json = "{\"title\":\"A\",\"content\":\"B\"}";
        mvc.perform(
                        post("/api/v1/announcements")
                                .session(admin)
                                .contentType("application/json")
                                .content(json))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post("/api/v1/announcements")
                                .session(admin)
                                .with(csrf())
                                .contentType("application/json")
                                .content("{\"title\":\" \",\"content\":\"B\"}"))
                .andExpect(status().isBadRequest());
        long last = 0;
        for (int i = 0; i < 2; i++) {
            var result =
                    mvc.perform(
                                    post("/api/v1/announcements")
                                            .session(admin)
                                            .with(csrf())
                                            .contentType("application/json")
                                            .content(json))
                            .andExpect(status().isCreated())
                            .andReturn();
            last = mapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        }
        jdbc.update("UPDATE announcements SET created_at='2026-01-01T00:00:00Z'");
        mvc.perform(get("/api/v1/announcements").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(last))
                .andExpect(jsonPath("$.totalElements").value(2));
        jdbc.update("UPDATE announcements SET deleted_at=now() WHERE id=?", last);
        mvc.perform(get("/api/v1/announcements/" + last)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/announcements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        jdbc.update("UPDATE users SET role='USER' WHERE id=?", users.get("ADMIN"));
        mvc.perform(
                        post("/api/v1/announcements")
                                .session(admin)
                                .with(csrf())
                                .contentType("application/json")
                                .content(json))
                .andExpect(status().isForbidden());
    }
}
