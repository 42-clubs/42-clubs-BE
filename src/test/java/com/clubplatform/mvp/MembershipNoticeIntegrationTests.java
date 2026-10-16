package com.clubplatform.mvp;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.clubplatform.club.entity.Club;
import com.clubplatform.club.repository.ClubRepository;
import com.clubplatform.club.service.ClubMembershipService;
import com.clubplatform.global.security.AuthenticatedUser;
import com.clubplatform.support.PostgreSqlTestConfig;
import com.clubplatform.user.service.UserAccountService;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgreSqlTestConfig.class)
class MembershipNoticeIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ClubRepository clubs;
    @Autowired UserAccountService accounts;
    @Autowired ClubMembershipService membership;
    long admin, user, other, club;

    @BeforeEach
    void setup() {
        clean();
        jdbc.update("DELETE FROM club_managers");
        jdbc.update("DELETE FROM clubs");
        jdbc.update("DELETE FROM users");
        admin = accounts.synchronize42Profile(4001, "admin", "Admin").id();
        user = accounts.synchronize42Profile(4002, "member", "Member").id();
        other = accounts.synchronize42Profile(4003, "other", "Other").id();
        jdbc.update("UPDATE users SET role='ADMIN' WHERE id=?", admin);
        club = clubs.saveAndFlush(new Club("Club", "Summary", "Description")).getId();
        jdbc.update(
                "INSERT INTO club_managers(user_id,club_id,created_at) VALUES (?,?,now())",
                user,
                club);
    }

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM notices");
        jdbc.update("DELETE FROM club_members");
    }

    MockHttpSession session(long id) {
        var identity = accounts.findIdentity(id).orElseThrow();
        var ctx = SecurityContextHolder.createEmptyContext();
        var principal = new AuthenticatedUser(identity.id(), identity.role());
        ctx.setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, principal.getAuthorities()));
        var session = new MockHttpSession();
        session.setAttribute("SPRING_SECURITY_CONTEXT", ctx);
        return session;
    }

    @Test
    void membershipPermissionsIdempotencyRejoinAndArchive() throws Exception {
        String uri = "/api/v1/admin/clubs/" + club + "/members/" + user;
        mvc.perform(put(uri).session(session(admin))).andExpect(status().isForbidden());
        mvc.perform(put(uri).session(session(user)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(put(uri).session(session(admin)).with(csrf()))
                .andExpect(status().isNoContent());
        var joined =
                jdbc.queryForObject(
                        "SELECT joined_at FROM club_members WHERE user_id=?",
                        java.time.OffsetDateTime.class,
                        user);
        mvc.perform(put(uri).session(session(admin)).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT joined_at FROM club_members WHERE user_id=?",
                                java.time.OffsetDateTime.class,
                                user))
                .isEqualTo(joined);
        mvc.perform(get("/api/v1/clubs/" + club + "/members").session(session(admin)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/clubs/" + club + "/members").session(session(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/admin/users/" + user + "/clubs").session(session(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeClubCount").value(1));
        jdbc.update(
                "UPDATE club_members SET status='LEFT',left_at=joined_at WHERE user_id=?", user);
        mvc.perform(get("/api/v1/clubs/" + club + "/members").session(session(user)))
                .andExpect(status().isForbidden());
        mvc.perform(put(uri).session(session(admin)).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM club_members", Integer.class))
                .isEqualTo(1);
        jdbc.update("UPDATE clubs SET operation_status='ARCHIVED' WHERE id=?", club);
        mvc.perform(put(uri).session(session(admin)).with(csrf())).andExpect(status().isConflict());
    }

    @Test
    void concurrentMembershipAndNoAutomaticTwoClubLimit() throws Exception {
        var a = CompletableFuture.runAsync(() -> membership.register(admin, club, user));
        var b = CompletableFuture.runAsync(() -> membership.register(admin, club, user));
        CompletableFuture.allOf(a, b).get(15, TimeUnit.SECONDS);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM club_members", Integer.class))
                .isEqualTo(1);
        for (int i = 0; i < 2; i++) {
            long id = clubs.saveAndFlush(new Club("Extra" + i, "Summary", "Description")).getId();
            membership.register(admin, id, user);
        }
        assertThat(membership.memberships(admin, user).activeClubCount()).isEqualTo(3);
    }

    @Test
    void noticePublicReadsScopedWritingAndSoftDelete() throws Exception {
        String uri = "/api/v1/clubs/" + club + "/notices";
        String body = "{\"title\":\"Notice\",\"content\":\"Body\"}";
        mvc.perform(
                        post(uri)
                                .session(session(admin))
                                .with(csrf())
                                .contentType("application/json")
                                .content(body))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post(uri)
                                .session(session(user))
                                .with(csrf())
                                .contentType("application/json")
                                .content("{\"title\":\" \",\"content\":\"Body\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(uri).session(session(user)).contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post(uri)
                                .session(session(user))
                                .with(csrf())
                                .contentType("application/json")
                                .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"));
        long id = jdbc.queryForObject("SELECT id FROM notices", Long.class);
        mvc.perform(get(uri))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Notice"))
                .andExpect(jsonPath("$.content[0].content").doesNotExist());
        mvc.perform(get("/api/v1/notices/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorId").value(user));
        jdbc.update("UPDATE notices SET deleted_at=now() WHERE id=?", id);
        mvc.perform(get(uri))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/notices/" + id)).andExpect(status().isNotFound());
    }
}
