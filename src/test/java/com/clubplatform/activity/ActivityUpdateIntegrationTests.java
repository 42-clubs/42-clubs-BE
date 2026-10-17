package com.clubplatform.activity;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.clubplatform.activity.dto.ActivityUpdateRequest;
import com.clubplatform.activity.entity.*;
import com.clubplatform.activity.repository.*;
import com.clubplatform.activity.service.ActivityUpdateService;
import com.clubplatform.club.entity.Club;
import com.clubplatform.club.repository.ClubRepository;
import com.clubplatform.global.security.AuthenticatedUser;
import com.clubplatform.support.PostgreSqlTestConfig;
import com.clubplatform.user.service.UserAccountService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgreSqlTestConfig.class)
class ActivityUpdateIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ClubRepository clubs;
    @Autowired ActivityRepository activities;
    @Autowired ActivityParticipantRepository participants;
    @Autowired ActivityImageRepository images;
    @Autowired ActivityUpdateService updates;
    @Autowired UserAccountService accounts;
    @Autowired ObjectMapper mapper;
    final Map<String, Long> users = new HashMap<>();
    final Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
    long club, otherClub, activity;

    @BeforeEach
    void prepare() {
        clean();
        long intra = 18000;
        for (String role :
                List.of("ADMIN", "CHAIR", "NEW_CHAIR", "OTHER_CHAIR", "MEMBER", "OUTSIDER"))
            users.put(role, accounts.synchronize42Profile(++intra, role.toLowerCase(), role).id());
        jdbc.update("UPDATE users SET role='ADMIN' WHERE id=?", users.get("ADMIN"));
        club = clubs.saveAndFlush(new Club("A", "Summary", "Description")).getId();
        otherClub = clubs.saveAndFlush(new Club("B", "Summary", "Description")).getId();
        for (String role : List.of("CHAIR", "NEW_CHAIR", "OTHER_CHAIR"))
            jdbc.update(
                    "INSERT INTO club_managers(user_id,club_id,created_at) VALUES(?,?,now())",
                    users.get(role),
                    role.equals("OTHER_CHAIR") ? otherClub : club);
        activity =
                activities
                        .saveAndFlush(
                                new Activity(
                                        club,
                                        users.get("CHAIR"),
                                        "Original",
                                        "Original body",
                                        createdAt.plusSeconds(7200),
                                        createdAt.plusSeconds(3600),
                                        5,
                                        false,
                                        createdAt))
                        .getId();
        for (String role : List.of("CHAIR", "MEMBER")) {
            participants.saveAndFlush(
                    new ActivityParticipant(activity, users.get(role), createdAt));
            jdbc.update(
                    "INSERT INTO club_members(user_id,club_id,status,approved_by,joined_at,created_at,updated_at) VALUES(?,?,'ACTIVE',?,now(),now(),now())",
                    users.get(role),
                    club,
                    users.get("ADMIN"));
        }
        jdbc.update(
                "UPDATE activity_participants SET attendance_status='PRESENT',attendance_checked_by=?,attendance_checked_at=? WHERE activity_id=? AND user_id=?",
                users.get("CHAIR"),
                java.sql.Timestamp.from(createdAt),
                activity,
                users.get("MEMBER"));
        images.saveAndFlush(
                new ActivityImage(
                        activity,
                        "activities/00000000-0000-0000-0000-000000000001.png",
                        0,
                        createdAt));
    }

    @AfterEach
    void clean() {
        for (String table :
                List.of(
                        "activity_images",
                        "activity_participants",
                        "activities",
                        "image_uploads",
                        "gathering_session_participants",
                        "gathering_sessions",
                        "gatherings",
                        "announcements",
                        "notices",
                        "club_members",
                        "club_managers",
                        "clubs",
                        "users")) jdbc.update("DELETE FROM " + table);
    }

    MockHttpSession session(String role) {
        var identity = accounts.findIdentity(users.get(role)).orElseThrow();
        var principal = new AuthenticatedUser(identity.id(), identity.role());
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, principal.getAuthorities()));
        var session = new MockHttpSession();
        session.setAttribute("SPRING_SECURITY_CONTEXT", context);
        return session;
    }

    String path() {
        return "/api/v1/clubs/" + club + "/activities/" + activity;
    }

    Map<String, Object> row() {
        return jdbc.queryForMap("SELECT * FROM activities WHERE id=?", activity);
    }

    @ParameterizedTest
    @CsvSource({
        "ADMIN,204",
        "CHAIR,204",
        "NEW_CHAIR,204",
        "OTHER_CHAIR,403",
        "MEMBER,403",
        "OUTSIDER,403"
    })
    void currentChairOrAdminCanEditPastActivityWithConfirmedAttendance(String role, int expected)
            throws Exception {
        var before = row();
        var attendance = jdbc.queryForList("SELECT * FROM activity_participants ORDER BY id");
        var photos = jdbc.queryForList("SELECT * FROM activity_images ORDER BY id");
        mvc.perform(
                        patch(path())
                                .session(session(role))
                                .with(csrf())
                                .contentType("application/json")
                                .content("{\"title\":\"Renamed\",\"content\":\"New body\"}"))
                .andExpect(status().is(expected));
        var after = row();
        if (expected == 204) {
            assertThat(after)
                    .containsEntry("title", "Renamed")
                    .containsEntry("content", "New body");
            before.keySet().removeAll(Set.of("title", "content", "updated_at"));
            assertThat(after).containsAllEntriesOf(before);
        } else assertThat(after).isEqualTo(before);
        assertThat(jdbc.queryForList("SELECT * FROM activity_participants ORDER BY id"))
                .isEqualTo(attendance);
        assertThat(jdbc.queryForList("SELECT * FROM activity_images ORDER BY id"))
                .isEqualTo(photos);
    }

    @Test
    void omittedFieldsAndEqualRetriesArePreservedAndQueriesSeeUpdatedTitle() throws Exception {
        var admin = session("ADMIN");
        mvc.perform(
                        patch(path())
                                .session(admin)
                                .with(csrf())
                                .contentType("application/json")
                                .content("{\"title\":\"Renamed\"}"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        var first = row();
        assertThat(first).containsEntry("content", "Original body");
        mvc.perform(
                        patch(path())
                                .session(admin)
                                .with(csrf())
                                .contentType("application/json")
                                .content("{\"title\":\"Renamed\"}"))
                .andExpect(status().isNoContent());
        assertThat(row()).isEqualTo(first);
        mvc.perform(
                        patch(path())
                                .session(admin)
                                .with(csrf())
                                .contentType("application/json")
                                .content("{\"content\":\"Body only\"}"))
                .andExpect(status().isNoContent());
        assertThat(row()).containsEntry("title", "Renamed").containsEntry("content", "Body only");
        mvc.perform(get(path()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Renamed"))
                .andExpect(jsonPath("$.content").value("Body only"))
                .andExpect(jsonPath("$.currentParticipantCount").value(2))
                .andExpect(jsonPath("$.presentParticipantCount").value(1));
        mvc.perform(get("/api/v1/clubs/" + club + "/activities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Renamed"));
        mvc.perform(get("/api/v1/clubs/" + club))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recentActivity.title").value("Renamed"));
        mvc.perform(
                        get("/api/v1/me/participations")
                                .param("period", "PAST")
                                .session(session("MEMBER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].items[0].title").value("Renamed"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "[]",
                "null",
                "\"text\"",
                "{\"title\":null}",
                "{\"content\":null}",
                "{\"title\":\" \"}",
                "{\"content\":\"\"}",
                "{\"title\":7}",
                "{\"content\":true}",
                "{\"title\":\"Valid\",\"content\":null}",
                "{\"title\":\"Valid\",\"content\":\" \"}",
                "{\"title\":\"Valid\",\"allowExternalParticipants\":true}",
                "{\"capacity\":10}",
                "{\"startsAt\":\"2027-01-01T00:00:00Z\"}",
                "{\"recruitmentClosesAt\":\"2027-01-01T00:00:00Z\"}",
                "{\"images\":[]}",
                "{\"organizerId\":1}",
                "{\"authorId\":1}",
                "{\"clubId\":1}",
                "{\"deletedAt\":null}"
            })
    void invalidAndUnsupportedFieldsRejectTheWholeRequest(String body) throws Exception {
        var before = row();
        mvc.perform(
                        patch(path())
                                .session(session("ADMIN"))
                                .with(csrf())
                                .contentType("application/json")
                                .content(body))
                .andExpect(status().isBadRequest());
        assertThat(row()).isEqualTo(before);
    }

    @Test
    void titleAndContentLengthLimitsApplyAtomically() throws Exception {
        var admin = session("ADMIN");
        var valid = Map.of("title", "가".repeat(200), "content", "나".repeat(50000));
        mvc.perform(
                        patch(path())
                                .session(admin)
                                .with(csrf())
                                .contentType("application/json")
                                .content(mapper.writeValueAsString(valid)))
                .andExpect(status().isNoContent());
        var before = row();
        for (var invalid :
                List.of(
                        Map.of("title", "가".repeat(201), "content", "Must not persist"),
                        Map.of("title", "Must not persist", "content", "나".repeat(50001))))
            mvc.perform(
                            patch(path())
                                    .session(admin)
                                    .with(csrf())
                                    .contentType("application/json")
                                    .content(mapper.writeValueAsString(invalid)))
                    .andExpect(status().isBadRequest());
        assertThat(row()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "CHAIR", "NEW_CHAIR"})
    void archiveRejectsTextAndAttendanceChangesForEveryRole(String role) throws Exception {
        var actor = session(role);
        jdbc.update("UPDATE clubs SET operation_status='ARCHIVED' WHERE id=?", club);
        var before = row();
        var attendance = jdbc.queryForList("SELECT * FROM activity_participants ORDER BY id");
        mvc.perform(
                        patch(path())
                                .session(actor)
                                .with(csrf())
                                .contentType("application/json")
                                .content("{\"title\":\"Denied\"}"))
                .andExpect(status().isForbidden());
        for (String state : List.of("UNCHECKED", "PRESENT"))
            mvc.perform(
                            patch(path() + "/participants/" + users.get("MEMBER") + "/attendance")
                                    .session(actor)
                                    .with(csrf())
                                    .contentType("application/json")
                                    .content("{\"attendanceStatus\":\"" + state + "\"}"))
                    .andExpect(status().isForbidden());
        assertThat(row()).isEqualTo(before);
        assertThat(jdbc.queryForList("SELECT * FROM activity_participants ORDER BY id"))
                .isEqualTo(attendance);
        mvc.perform(post(path() + "/participants").session(actor).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(delete(path() + "/participants/me").session(actor).with(csrf()))
                .andExpect(status().isForbidden());
        var admin = session("ADMIN");
        String managerPath = "/api/v1/admin/clubs/" + club + "/managers/" + users.get("CHAIR");
        mvc.perform(put(managerPath).session(admin).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(delete(managerPath).session(admin).with(csrf()))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForList("SELECT * FROM activity_participants ORDER BY id"))
                .isEqualTo(attendance);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM club_managers WHERE club_id=?",
                                Long.class,
                                club))
                .isEqualTo(2);
        mvc.perform(get(path())).andExpect(status().isOk());
        mvc.perform(get(path() + "/participants").session(session("CHAIR")))
                .andExpect(status().isOk());
        mvc.perform(get(path() + "/participants/me").session(session("MEMBER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attendanceStatus").value("PRESENT"));
    }

    @Test
    void roleRevocationAndCurrentChairAreIndependentOfOriginalOrganizer() throws Exception {
        var former = session("CHAIR");
        var admin = session("ADMIN");
        jdbc.update(
                "DELETE FROM club_managers WHERE user_id=? AND club_id=?",
                users.get("CHAIR"),
                club);
        jdbc.update("UPDATE users SET role='USER' WHERE id=?", users.get("ADMIN"));
        for (var actor : List.of(former, admin))
            mvc.perform(
                            patch(path())
                                    .session(actor)
                                    .with(csrf())
                                    .contentType("application/json")
                                    .content("{\"title\":\"Denied\"}"))
                    .andExpect(status().isForbidden());
        mvc.perform(
                        patch(path())
                                .session(session("NEW_CHAIR"))
                                .with(csrf())
                                .contentType("application/json")
                                .content("{\"title\":\"New chair title\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(get(path() + "/participants").session(session("NEW_CHAIR")))
                .andExpect(status().isForbidden());
        mvc.perform(get(path() + "/participants").session(former)).andExpect(status().isOk());
        assertThat(row())
                .containsEntry("organizer_id", users.get("CHAIR"))
                .containsEntry("author_id", users.get("CHAIR"));
    }

    @Test
    void authenticationCsrfMissingDeletedAndCrossClubTargetsAreRejected() throws Exception {
        String body = "{\"title\":\"Denied\"}";
        var admin = session("ADMIN");
        var before = row();
        mvc.perform(patch(path()).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch(path()).session(admin).contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        mvc.perform(
                        patch(path())
                                .session(admin)
                                .with(csrf().useInvalidToken())
                                .contentType("application/json")
                                .content(body))
                .andExpect(status().isForbidden());
        for (String url :
                List.of(
                        "/api/v1/clubs/" + otherClub + "/activities/" + activity,
                        "/api/v1/clubs/" + club + "/activities/9223372036854775807",
                        "/api/v1/clubs/9223372036854775807/activities/" + activity))
            mvc.perform(
                            patch(url)
                                    .session(admin)
                                    .with(csrf())
                                    .contentType("application/json")
                                    .content(body))
                    .andExpect(status().isNotFound());
        for (String url :
                List.of(
                        "/api/v1/clubs/0/activities/" + activity,
                        "/api/v1/clubs/" + club + "/activities/0",
                        "/api/v1/clubs/" + club + "/activities/invalid"))
            mvc.perform(
                            patch(url)
                                    .session(admin)
                                    .with(csrf())
                                    .contentType("application/json")
                                    .content(body))
                    .andExpect(status().isBadRequest());
        assertThat(row()).isEqualTo(before);
        jdbc.update("UPDATE activities SET deleted_at=now() WHERE id=?", activity);
        var deleted = row();
        mvc.perform(
                        patch(path())
                                .session(admin)
                                .with(csrf())
                                .contentType("application/json")
                                .content(body))
                .andExpect(status().isNotFound());
        assertThat(row()).isEqualTo(deleted);
    }

    @Test
    void concurrentPartialUpdatesPreserveBothFieldsAndParticipants() throws Exception {
        var a =
                CompletableFuture.runAsync(
                        () ->
                                updates.update(
                                        users.get("ADMIN"),
                                        club,
                                        activity,
                                        new ActivityUpdateRequest("Concurrent title", null)));
        var b =
                CompletableFuture.runAsync(
                        () ->
                                updates.update(
                                        users.get("NEW_CHAIR"),
                                        club,
                                        activity,
                                        new ActivityUpdateRequest(null, "Concurrent body")));
        CompletableFuture.allOf(a, b).get(15, TimeUnit.SECONDS);
        assertThat(row())
                .containsEntry("title", "Concurrent title")
                .containsEntry("content", "Concurrent body");
        assertThat(participants.countByActivityIdAndStatus(activity, "CONFIRMED")).isEqualTo(2);
    }
}
