package com.clubplatform.club;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.clubplatform.club.dto.ClubUpdateRequest;
import com.clubplatform.club.entity.Club;
import com.clubplatform.club.entity.OperationStatus;
import com.clubplatform.club.repository.ClubRepository;
import com.clubplatform.club.service.ClubUpdateService;
import com.clubplatform.global.security.AuthenticatedUser;
import com.clubplatform.support.PostgreSqlTestConfig;
import com.clubplatform.user.service.UserAccountService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgreSqlTestConfig.class)
class ClubUpdateIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ClubRepository clubs;
    @Autowired UserAccountService accounts;
    @Autowired ClubUpdateService updates;
    @Autowired ObjectMapper mapper;
    long admin, manager, otherManager, member, outsider, club, otherClub;

    @BeforeEach
    void prepare() {
        clear();
        admin = accounts.synchronize42Profile(8101, "admin", "Admin").id();
        manager = accounts.synchronize42Profile(8102, "chair", "Chair").id();
        otherManager = accounts.synchronize42Profile(8103, "otherchair", "Other chair").id();
        member = accounts.synchronize42Profile(8104, "member", "Member").id();
        outsider = accounts.synchronize42Profile(8105, "outsider", "Outsider").id();
        jdbc.update("UPDATE users SET role='ADMIN' WHERE id=?", admin);
        club =
                clubs.saveAndFlush(new Club("Original", "Original summary", "Original description"))
                        .getId();
        otherClub =
                clubs.saveAndFlush(new Club("Other", "Other summary", "Other description")).getId();
        jdbc.update(
                "INSERT INTO club_managers(user_id,club_id,created_at) VALUES(?,?,now()),(?,?,now())",
                manager,
                club,
                otherManager,
                otherClub);
        jdbc.update(
                "INSERT INTO club_members(user_id,club_id,status,approved_by,joined_at,created_at,updated_at) VALUES(?,?,'ACTIVE',?,now(),now(),now())",
                member,
                club,
                admin);
    }

    @AfterEach
    void clear() {
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
                        "users")) {
            jdbc.update("DELETE FROM " + table);
        }
    }

    @ParameterizedTest
    @EnumSource(OperationStatus.class)
    void adminCanUpdateAllFieldsOnlyWhileActive(OperationStatus status) throws Exception {
        jdbc.update("UPDATE clubs SET operation_status=? WHERE id=?", status.name(), club);
        var before = row();
        if (status == OperationStatus.ARCHIVED) {
            change(admin, Map.of("name", "Denied", "summary", "Denied", "description", "Denied"))
                    .andExpect(status().isForbidden());
            assertThat(row()).isEqualTo(before);
            return;
        }
        change(
                        admin,
                        Map.of(
                                "name",
                                "Renamed",
                                "summary",
                                "New summary",
                                "description",
                                "New description"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        var after = row();
        assertThat(after)
                .containsEntry("name", "Renamed")
                .containsEntry("summary", "New summary")
                .containsEntry("description", "New description");
        assertThat(preserved(after)).isEqualTo(preserved(before));
        assertThat(after.get("updated_at")).isNotEqualTo(before.get("updated_at"));
        mvc.perform(get(path()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.summary").value("New summary"))
                .andExpect(jsonPath("$.description").value("New description"))
                .andExpect(jsonPath("$.operationStatus").value(status.name()));
        mvc.perform(get("/api/v1/clubs").param("operationStatus", status.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == %s)].name", club).value("Renamed"));
    }

    @Test
    void activeManagerCanUpdateSummaryAndDescriptionWithoutMembershipAndOmittedFieldsStay()
            throws Exception {
        change(manager, Map.of("summary", "Chair summary")).andExpect(status().isNoContent());
        assertThat(row())
                .containsEntry("name", "Original")
                .containsEntry("summary", "Chair summary")
                .containsEntry("description", "Original description");
        change(manager, Map.of("description", "Chair introduction"))
                .andExpect(status().isNoContent());
        assertThat(row())
                .containsEntry("name", "Original")
                .containsEntry("summary", "Chair summary")
                .containsEntry("description", "Chair introduction");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM club_members WHERE user_id=?",
                                Long.class,
                                manager))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT name FROM clubs WHERE id=?", String.class, otherClub))
                .isEqualTo("Other");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Renamed", "Original"})
    void managerCannotIncludeNameAndMixedRequestChangesNothing(String name) throws Exception {
        var before = row();
        change(
                        manager,
                        Map.of(
                                "name",
                                name,
                                "summary",
                                "Must not persist",
                                "description",
                                "Must not persist"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        assertThat(row()).isEqualTo(before);
    }

    @Test
    void unrelatedManagerMemberAndOutsiderCannotUpdateAndArchivedManagerIsDenied()
            throws Exception {
        var before = row();
        for (long actor : List.of(otherManager, member, outsider)) {
            change(actor, Map.of("summary", "Denied")).andExpect(status().isForbidden());
        }
        assertThat(row()).isEqualTo(before);
        jdbc.update("UPDATE clubs SET operation_status='ARCHIVED' WHERE id=?", club);
        var archived = row();
        change(manager, Map.of("summary", "Denied", "description", "Denied"))
                .andExpect(status().isForbidden());
        assertThat(row()).isEqualTo(archived);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "[]",
                "null",
                "\"text\"",
                "{\"name\":null}",
                "{\"summary\":null}",
                "{\"description\":null}",
                "{\"name\":\"\"}",
                "{\"summary\":\"   \"}",
                "{\"description\":\"\"}",
                "{\"name\":7}",
                "{\"summary\":true}",
                "{\"description\":{}}",
                "{\"name\":\"Valid\",\"summary\":null}",
                "{\"name\":\"Valid\",\"description\":\"   \"}",
                "{\"name\":\"Valid\",\"operationStatus\":\"ARCHIVED\"}",
                "{\"recruitment\":{}}",
                "{\"contact\":\"contact\"}",
                "{\"allowExternalParticipants\":true}"
            })
    void malformedUnsupportedAndNullFieldsReturn400WithoutPartialChanges(String body)
            throws Exception {
        var before = row();
        mvc.perform(
                        patch(path())
                                .session(session(admin))
                                .with(csrf())
                                .contentType("application/json")
                                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        assertThat(row()).isEqualTo(before);
    }

    @Test
    void lengthBoundariesAndIdempotentRetryPreserveMetadata() throws Exception {
        var before = row();
        change(admin, Map.of("name", "n".repeat(101), "summary", "Must not persist"))
                .andExpect(status().isBadRequest());
        change(admin, Map.of("name", "Must not persist", "summary", "s".repeat(201)))
                .andExpect(status().isBadRequest());
        assertThat(row()).isEqualTo(before);
        var boundary = Map.of("name", "n".repeat(100), "summary", "s".repeat(200));
        change(admin, boundary).andExpect(status().isNoContent());
        var changed = row();
        change(admin, boundary).andExpect(status().isNoContent());
        assertThat(row()).isEqualTo(changed);
    }

    @Test
    void authenticationCsrfAndPathValidationStayEnforced() throws Exception {
        var before = row();
        mvc.perform(
                        patch(path())
                                .with(csrf())
                                .contentType("application/json")
                                .content("{\"summary\":\"Value\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(
                        patch(path())
                                .session(session(admin))
                                .contentType("application/json")
                                .content("{\"summary\":\"Value\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(
                        patch(path())
                                .session(session(admin))
                                .with(csrf().useInvalidToken())
                                .contentType("application/json")
                                .content("{\"summary\":\"Value\"}"))
                .andExpect(status().isForbidden());
        for (String id : List.of("0", "-1", "invalid")) {
            mvc.perform(
                            patch("/api/v1/clubs/" + id)
                                    .session(session(admin))
                                    .with(csrf())
                                    .contentType("application/json")
                                    .content("{\"summary\":\"Value\"}"))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(
                        patch("/api/v1/clubs/99999999")
                                .session(session(admin))
                                .with(csrf())
                                .contentType("application/json")
                                .content("{\"summary\":\"Value\"}"))
                .andExpect(status().isNotFound());
        assertThat(row()).isEqualTo(before);
    }

    @Test
    void revokedManagerAndAdminLosePermissionInExistingSessions() throws Exception {
        var chairSession = session(manager);
        var adminSession = session(admin);
        mvc.perform(
                        patch(path())
                                .session(chairSession)
                                .with(csrf())
                                .contentType("application/json")
                                .content("{\"summary\":\"Before revoke\"}"))
                .andExpect(status().isNoContent());
        jdbc.update("DELETE FROM club_managers WHERE user_id=? AND club_id=?", manager, club);
        mvc.perform(
                        patch(path())
                                .session(chairSession)
                                .with(csrf())
                                .contentType("application/json")
                                .content("{\"summary\":\"Denied\"}"))
                .andExpect(status().isForbidden());
        jdbc.update("UPDATE users SET role='USER' WHERE id=?", admin);
        mvc.perform(
                        patch(path())
                                .session(adminSession)
                                .with(csrf())
                                .contentType("application/json")
                                .content("{\"name\":\"Denied\"}"))
                .andExpect(status().isForbidden());
        assertThat(row())
                .containsEntry("name", "Original")
                .containsEntry("summary", "Before revoke");
    }

    @Test
    void relatedReadsUseNewNameWhileRelationshipsAndActivitiesStayIntact() throws Exception {
        long activity =
                jdbc.queryForObject(
                        "INSERT INTO activities(club_id,author_id,organizer_id,title,content,starts_at,recruitment_closes_at,capacity,allow_external_participants,created_at,updated_at) VALUES(?,?,?,'Activity','Body',now()+interval '2 hours',now()+interval '1 hour',3,true,now(),now()) RETURNING id",
                        Long.class,
                        club,
                        manager,
                        manager);
        jdbc.update(
                "INSERT INTO activity_participants(activity_id,user_id,status,attendance_status,created_at,updated_at) VALUES(?,?,'CONFIRMED','UNCHECKED',now(),now()),(?,?,'CONFIRMED','UNCHECKED',now(),now())",
                activity,
                manager,
                activity,
                member);
        change(admin, Map.of("name", "Updated club")).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/me/clubs").session(session(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Updated club"));
        mvc.perform(get("/api/v1/admin/users/{id}/clubs", member).session(session(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clubs[0].name").value("Updated club"));
        mvc.perform(get("/api/v1/me/participations").session(session(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Updated club"))
                .andExpect(jsonPath("$.content[0].items[0].activityId").value(activity));
        mvc.perform(get(path()))
                .andExpect(jsonPath("$.memberCount").value(2))
                .andExpect(jsonPath("$.managers[0].intraLogin").value("chair"));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM activity_participants WHERE activity_id=?",
                                Long.class,
                                activity))
                .isEqualTo(2);
    }

    @Test
    void concurrentPatchesToDifferentFieldsPreserveBothUpdates() throws Exception {
        var barrier = new CyclicBarrier(2);
        var tasks = new ArrayList<CompletableFuture<Void>>();
        for (boolean rename : List.of(true, false))
            tasks.add(
                    CompletableFuture.runAsync(
                            () -> {
                                try {
                                    barrier.await(10, TimeUnit.SECONDS);
                                    updates.update(
                                            rename ? admin : manager,
                                            club,
                                            rename
                                                    ? new ClubUpdateRequest(
                                                            "Concurrent name", null, null)
                                                    : new ClubUpdateRequest(
                                                            null, "Concurrent summary", null));
                                } catch (Exception exception) {
                                    throw new RuntimeException(exception);
                                }
                            }));
        CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).get(20, TimeUnit.SECONDS);
        assertThat(row())
                .containsEntry("name", "Concurrent name")
                .containsEntry("summary", "Concurrent summary")
                .containsEntry("description", "Original description");
    }

    private ResultActions change(long actor, Map<String, String> fields) throws Exception {
        return mvc.perform(
                patch(path())
                        .session(session(actor))
                        .with(csrf())
                        .contentType("application/json")
                        .content(mapper.writeValueAsBytes(fields)));
    }

    private String path() {
        return "/api/v1/clubs/" + club;
    }

    private Map<String, Object> row() {
        return jdbc.queryForMap("SELECT * FROM clubs WHERE id=?", club);
    }

    private Map<String, Object> preserved(Map<String, Object> row) {
        var result = new HashMap<>(row);
        for (String field : List.of("name", "summary", "description", "updated_at"))
            result.remove(field);
        return result;
    }

    private MockHttpSession session(long id) {
        var principal = new AuthenticatedUser(id, accounts.findIdentity(id).orElseThrow().role());
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, principal.getAuthorities()));
        var session = new MockHttpSession();
        session.setAttribute("SPRING_SECURITY_CONTEXT", context);
        return session;
    }
}
