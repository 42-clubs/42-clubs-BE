package com.clubplatform.activity;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.clubplatform.activity.storage.LocalPhotoStorage;
import com.clubplatform.club.entity.Club;
import com.clubplatform.club.repository.ClubRepository;
import com.clubplatform.global.security.AuthenticatedUser;
import com.clubplatform.support.PostgreSqlTestConfig;
import com.clubplatform.user.service.UserAccountService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgreSqlTestConfig.class)
class ActivityEligibilityIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ClubRepository clubs;
    @Autowired UserAccountService accounts;
    @Autowired ObjectMapper mapper;
    @MockitoBean LocalPhotoStorage storage;
    long chair, member, outsider, admin, otherChair, left, otherMember, club, otherClub;

    @BeforeEach
    void prepare() {
        for (String table :
                List.of(
                        "activity_images",
                        "activity_participants",
                        "activities",
                        "image_uploads",
                        "club_members",
                        "club_managers",
                        "clubs",
                        "users")) {
            jdbc.update("DELETE FROM " + table);
        }
        chair = accounts.synchronize42Profile(7101, "chair", "Chair").id();
        member = accounts.synchronize42Profile(7102, "member", "Member").id();
        outsider = accounts.synchronize42Profile(7103, "outsider", "Outsider").id();
        admin = accounts.synchronize42Profile(7104, "admin", "Admin").id();
        otherChair = accounts.synchronize42Profile(7105, "otherchair", "Other chair").id();
        left = accounts.synchronize42Profile(7106, "left", "Left").id();
        otherMember = accounts.synchronize42Profile(7107, "othermember", "Other member").id();
        jdbc.update("UPDATE users SET role='ADMIN' WHERE id=?", admin);
        club = clubs.saveAndFlush(new Club("Eligibility", "Summary", "Body")).getId();
        otherClub = clubs.saveAndFlush(new Club("Other", "Summary", "Body")).getId();
        for (long id : List.of(chair, otherChair)) {
            jdbc.update(
                    "INSERT INTO club_managers(user_id,club_id,created_at) VALUES(?,?,now())",
                    id,
                    club);
        }
        register(chair, club);
        register(member, club);
        register(left, club);
        register(otherMember, otherClub);
        leave(left);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void creationPersistsPolicyAndPublicListAndDetailExposeIt(boolean external) throws Exception {
        long id = create(external);
        mvc.perform(get(path(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowExternalParticipants").value(external))
                .andExpect(jsonPath("$.currentParticipantCount").value(1))
                .andExpect(jsonPath("$.presentParticipantCount").value(0));
        mvc.perform(get("/api/v1/clubs/{club}/activities", club))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].allowExternalParticipants").value(external));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT allow_external_participants FROM activities WHERE id=?",
                                Boolean.class,
                                id))
                .isEqualTo(external);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MISSING", "NULL", "INVALID"})
    void creationRequiresExplicitPolicyWithoutFallback(String value) throws Exception {
        var request = input(true);
        if (value.equals("MISSING")) request.remove("allowExternalParticipants");
        else request.put("allowExternalParticipants", value.equals("NULL") ? null : "invalid");
        createRequest(chair, request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM activities", Long.class)).isZero();
        verify(storage, never()).put(anyString(), any(), anyString());
    }

    @Test
    void memberOnlyCreationRequiresChairMembershipIncludingAdminChair() throws Exception {
        for (long actor : List.of(otherChair, chair)) {
            if (actor == chair) {
                leave(chair);
                jdbc.update("UPDATE users SET role='ADMIN' WHERE id=?", chair);
            }
            createRequest(actor, input(false)).andExpect(status().isForbidden());
            createRequest(actor, input(true)).andExpect(status().isCreated());
        }
        // Being an ACTIVE member without the chair relationship still does not permit creation.
        createRequest(member, input(false)).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM activities", Long.class)).isEqualTo(2);
        verify(storage, never()).put(anyString(), any(), anyString());
    }

    @Test
    void memberOnlyRejectsNonmembersLeftAdminAndManagerWithoutBypass() throws Exception {
        long id = create(false);
        for (long actor : List.of(outsider, admin, otherChair, left, otherMember)) {
            join(actor, id)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
        mvc.perform(post(path(id) + "/participants").with(csrf()))
                .andExpect(status().isUnauthorized());
        join(member, id).andExpect(status().isNoContent());
        join(member, id)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_JOINED"));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM activity_participants WHERE activity_id=?",
                                Long.class,
                                id))
                .isEqualTo(2);
    }

    @Test
    void externalAllowedStillPermitsLoggedInNonmembersButNeverAnonymous() throws Exception {
        long id = create(true);
        for (long actor : List.of(member, outsider, admin, otherChair, left, otherMember)) {
            join(actor, id).andExpect(status().isNoContent());
        }
        mvc.perform(post(path(id) + "/participants").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void membershipLossKeepsConfirmedParticipationAndAttendanceAndAllowsExistingActions()
            throws Exception {
        long id = create(false);
        join(member, id).andExpect(status().isNoContent());
        attendance(id, member, "PRESENT");
        leave(member);
        mine(member, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.attendanceStatus").value("PRESENT"));
        mvc.perform(get(path(id)))
                .andExpect(jsonPath("$.currentParticipantCount").value(2))
                .andExpect(jsonPath("$.presentParticipantCount").value(1));
        attendance(id, member, "UNCHECKED");
        mvc.perform(delete(path(id) + "/participants/me").session(session(member)).with(csrf()))
                .andExpect(status().isNoContent());
        join(member, id).andExpect(status().isForbidden());
        mine(member, id).andExpect(jsonPath("$.status").value("CANCELLED"));
        jdbc.update(
                "UPDATE club_members SET status='ACTIVE',left_at=NULL WHERE user_id=? AND club_id=?",
                member,
                club);
        join(member, id).andExpect(status().isNoContent());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM activity_participants WHERE activity_id=? AND user_id=?",
                                Long.class,
                                id,
                                member))
                .isEqualTo(1);
    }

    @Test
    void managerRevocationDoesNotRevokeMemberEligibilityOrOrganizerAttendance() throws Exception {
        long id = create(false);
        jdbc.update("DELETE FROM club_managers WHERE user_id=?", chair);
        mine(chair, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        attendance(id, chair, "PRESENT");
        // A former chair with ACTIVE membership can still join another member-only activity.
        register(otherChair, club);
        long next =
                mapper.readTree(
                                createRequest(otherChair, input(false))
                                        .andExpect(status().isCreated())
                                        .andReturn()
                                        .getResponse()
                                        .getContentAsString())
                        .get("id")
                        .asLong();
        join(chair, next).andExpect(status().isNoContent());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void policyCannotChangeImmediatelyAfterCreation(boolean external) throws Exception {
        long id = create(external);
        for (String method : List.of("PATCH", "PUT")) {
            var request = "PATCH".equals(method) ? patch(path(id)) : put(path(id));
            mvc.perform(
                            request.session(session(chair))
                                    .with(csrf())
                                    .contentType("application/json")
                                    .content(
                                            mapper.writeValueAsString(
                                                    Map.of(
                                                            "allowExternalParticipants",
                                                            !external))))
                    .andExpect(status().is("PATCH".equals(method) ? 400 : 405));
        }
        mvc.perform(get(path(id)))
                .andExpect(jsonPath("$.allowExternalParticipants").value(external));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM activity_participants WHERE activity_id=?",
                                Long.class,
                                id))
                .isEqualTo(1);
    }

    private long create(boolean external) throws Exception {
        return mapper.readTree(
                        createRequest(chair, input(external))
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString())
                .get("id")
                .asLong();
    }

    private Map<String, Object> input(boolean external) {
        var now = Instant.now();
        return new HashMap<>(
                Map.of(
                        "title",
                        "Policy activity",
                        "content",
                        "Body",
                        "startsAt",
                        now.plusSeconds(259200).toString(),
                        "recruitmentClosesAt",
                        now.plusSeconds(172800).toString(),
                        "capacity",
                        10,
                        "allowExternalParticipants",
                        external));
    }

    private ResultActions createRequest(long actor, Map<String, Object> input) throws Exception {
        return mvc.perform(
                multipart("/api/v1/clubs/{club}/activities", club)
                        .file(
                                new MockMultipartFile(
                                        "activity",
                                        "",
                                        "application/json",
                                        mapper.writeValueAsBytes(input)))
                        .session(session(actor))
                        .with(csrf()));
    }

    private ResultActions join(long actor, long id) throws Exception {
        return mvc.perform(post(path(id) + "/participants").session(session(actor)).with(csrf()));
    }

    private ResultActions mine(long actor, long id) throws Exception {
        return mvc.perform(get(path(id) + "/participants/me").session(session(actor)));
    }

    private void attendance(long id, long user, String status) throws Exception {
        mvc.perform(
                        patch(path(id) + "/participants/{user}/attendance", user)
                                .session(session(chair))
                                .with(csrf())
                                .contentType("application/json")
                                .content(
                                        mapper.writeValueAsString(
                                                Map.of("attendanceStatus", status))))
                .andExpect(status().isNoContent());
    }

    private void register(long user, long clubId) {
        jdbc.update(
                "INSERT INTO club_members(user_id,club_id,status,approved_by,joined_at,created_at,updated_at) VALUES(?,?,'ACTIVE',?,now(),now(),now())",
                user,
                clubId,
                admin);
    }

    private void leave(long user) {
        jdbc.update(
                "UPDATE club_members SET status='LEFT',left_at=now() WHERE user_id=? AND club_id=?",
                user,
                club);
    }

    private String path(long id) {
        return "/api/v1/clubs/" + club + "/activities/" + id;
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
