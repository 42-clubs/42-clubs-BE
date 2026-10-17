package com.clubplatform.notice;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.clubplatform.announcement.entity.Announcement;
import com.clubplatform.announcement.repository.AnnouncementRepository;
import com.clubplatform.announcement.service.AnnouncementService;
import com.clubplatform.club.entity.Club;
import com.clubplatform.club.repository.ClubRepository;
import com.clubplatform.club.service.ClubMembershipService;
import com.clubplatform.global.security.AuthenticatedUser;
import com.clubplatform.notice.entity.*;
import com.clubplatform.notice.repository.NoticeRepository;
import com.clubplatform.notice.service.NoticeService;
import com.clubplatform.support.PostgreSqlTestConfig;
import com.clubplatform.user.service.UserAccountService;
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
class NoticeDeletionIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserAccountService accounts;
    @Autowired ClubRepository clubs;
    @Autowired ClubMembershipService membership;
    @Autowired NoticeRepository notices;
    @Autowired AnnouncementRepository announcements;
    @Autowired NoticeService noticeService;
    @Autowired AnnouncementService announcementService;
    final Map<String, Long> users = new HashMap<>();
    final Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
    long club, otherClub, publicId, privateId, announcementId;

    @BeforeEach
    void setup() {
        clean();
        jdbc.update("DELETE FROM club_managers");
        jdbc.update("DELETE FROM clubs");
        jdbc.update("DELETE FROM users");
        long intra = 17000;
        for (String role :
                List.of(
                        "ADMIN",
                        "SECOND_ADMIN",
                        "MANAGER",
                        "OTHER_MANAGER",
                        "AUTHOR",
                        "MEMBER",
                        "OUTSIDER"))
            users.put(role, accounts.synchronize42Profile(++intra, role.toLowerCase(), role).id());
        for (String role : List.of("ADMIN", "SECOND_ADMIN"))
            jdbc.update("UPDATE users SET role='ADMIN' WHERE id=?", users.get(role));
        club = clubs.saveAndFlush(new Club("A", "Summary", "Description")).getId();
        otherClub = clubs.saveAndFlush(new Club("B", "Summary", "Description")).getId();
        for (String role : List.of("MANAGER", "OTHER_MANAGER"))
            jdbc.update(
                    "INSERT INTO club_managers(user_id,club_id,created_at) VALUES (?,?,now())",
                    users.get(role),
                    role.equals("MANAGER") ? club : otherClub);
        membership.register(users.get("ADMIN"), club, users.get("MEMBER"));
        publicId = notice(NoticeVisibility.PUBLIC);
        privateId = notice(NoticeVisibility.MEMBERS_ONLY);
        announcementId =
                announcements
                        .saveAndFlush(
                                new Announcement(
                                        users.get("ADMIN"), "Announcement", "Body", createdAt))
                        .getId();
    }

    long notice(NoticeVisibility visibility) {
        return notices.saveAndFlush(
                        new Notice(
                                club, users.get("AUTHOR"), "Notice", "Body", visibility, createdAt))
                .getId();
    }

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM announcements");
        jdbc.update("DELETE FROM notices");
        jdbc.update("DELETE FROM club_members");
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
        return "/api/v1/clubs/" + club + "/notices";
    }

    String announcementPath() {
        return "/api/v1/announcements/" + announcementId;
    }

    @ParameterizedTest
    @CsvSource({
        "MANAGER,204",
        "ADMIN,204",
        "SECOND_ADMIN,204",
        "OTHER_MANAGER,403",
        "AUTHOR,403",
        "MEMBER,403",
        "OUTSIDER,403"
    })
    void deletionUsesCurrentTargetClubRightsForBothVisibilities(String role, int expected)
            throws Exception {
        var actor = session(role);
        for (long id : List.of(publicId, privateId)) {
            mvc.perform(delete(path() + "/" + id).session(actor).with(csrf()))
                    .andExpect(status().is(expected));
            var row = notices.findById(id).orElseThrow();
            assertThat(row.getDeletedAt() != null).isEqualTo(expected == 204);
            assertThat(row.getAuthorId()).isEqualTo(users.get("AUTHOR"));
            assertThat(row.getTitle()).isEqualTo("Notice");
            assertThat(row.getContent()).isEqualTo("Body");
            assertThat(row.getCreatedAt()).isEqualTo(createdAt);
            if (expected == 204) assertThat(row.getUpdatedAt()).isEqualTo(row.getDeletedAt());
            else assertThat(row.getUpdatedAt()).isEqualTo(createdAt);
        }
        assertThat(announcements.findById(announcementId).orElseThrow().getDeletedAt()).isNull();
    }

    @ParameterizedTest
    @CsvSource({"MANAGER,403", "ADMIN,403", "SECOND_ADMIN,403"})
    void archiveDeletionIsDeniedForEveryRole(String role, int expected) throws Exception {
        jdbc.update("UPDATE clubs SET operation_status='ARCHIVED' WHERE id=?", club);
        mvc.perform(delete(path() + "/" + privateId).session(session(role)).with(csrf()))
                .andExpect(status().is(expected));
        assertThat(notices.findById(privateId).orElseThrow().getDeletedAt() != null)
                .isEqualTo(expected == 204);
    }

    @ParameterizedTest
    @CsvSource({
        "ADMIN,204",
        "SECOND_ADMIN,204",
        "MANAGER,403",
        "AUTHOR,403",
        "MEMBER,403",
        "OUTSIDER,403"
    })
    void anyCurrentAdminCanDeleteOperatingNotices(String role, int expected) throws Exception {
        mvc.perform(delete(announcementPath()).session(session(role)).with(csrf()))
                .andExpect(status().is(expected));
        var row = announcements.findById(announcementId).orElseThrow();
        assertThat(row.getDeletedAt() != null).isEqualTo(expected == 204);
        assertThat(row.getAuthorId()).isEqualTo(users.get("ADMIN"));
        assertThat(row.getTitle()).isEqualTo("Announcement");
        assertThat(row.getContent()).isEqualTo("Body");
        assertThat(row.getCreatedAt()).isEqualTo(createdAt);
        assertThat(notices.findById(publicId).orElseThrow().getDeletedAt()).isNull();
    }

    @Test
    void deletedNoticesDisappearFromAllListsDetailsAndPagination() throws Exception {
        var admin = session("ADMIN");
        for (long id : List.of(publicId, privateId))
            mvc.perform(delete(path() + "/" + id).session(admin).with(csrf()))
                    .andExpect(status().isNoContent());
        mvc.perform(delete(announcementPath()).session(admin).with(csrf()))
                .andExpect(status().isNoContent());
        for (String list : List.of(path(), "/api/v1/announcements")) {
            mvc.perform(get(list).param("size", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(0))
                    .andExpect(jsonPath("$.totalPages").value(0))
                    .andExpect(jsonPath("$.content").isEmpty());
            mvc.perform(get(list).session(admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(0));
        }
        for (String detail :
                List.of(path() + "/" + publicId, path() + "/" + privateId, announcementPath())) {
            mvc.perform(get(detail)).andExpect(status().isNotFound());
            mvc.perform(get(detail).session(admin)).andExpect(status().isNotFound());
        }
        assertThat(notices.count()).isEqualTo(2);
        assertThat(announcements.count()).isEqualTo(1);
    }

    @Test
    void retriesPreserveFirstDeletionAndRecheckCurrentPermissions() throws Exception {
        mvc.perform(delete(path() + "/" + privateId).session(session("MANAGER")).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(delete(announcementPath()).session(session("ADMIN")).with(csrf()))
                .andExpect(status().isNoContent());
        var clubDeletedAt = notices.findById(privateId).orElseThrow().getDeletedAt();
        var operatingDeletedAt =
                announcements.findById(announcementId).orElseThrow().getDeletedAt();
        mvc.perform(delete(path() + "/" + privateId).session(session("ADMIN")).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(delete(announcementPath()).session(session("SECOND_ADMIN")).with(csrf()))
                .andExpect(status().isNoContent());
        for (String url : List.of(path() + "/" + privateId, announcementPath()))
            mvc.perform(delete(url).session(session("OUTSIDER")).with(csrf()))
                    .andExpect(status().isForbidden());
        var clubRow = notices.findById(privateId).orElseThrow();
        assertThat(clubRow.getDeletedAt()).isEqualTo(clubDeletedAt);
        assertThat(clubRow.getUpdatedAt()).isEqualTo(clubDeletedAt);
        var operatingRow = announcements.findById(announcementId).orElseThrow();
        assertThat(operatingRow.getDeletedAt()).isEqualTo(operatingDeletedAt);
        assertThat(operatingRow.getUpdatedAt()).isEqualTo(operatingDeletedAt);
    }

    @Test
    void archivePolicyAlsoAppliesToAlreadyDeletedNotices() throws Exception {
        var manager = session("MANAGER");
        mvc.perform(delete(path() + "/" + publicId).session(manager).with(csrf()))
                .andExpect(status().isNoContent());
        jdbc.update("UPDATE clubs SET operation_status='ARCHIVED' WHERE id=?", club);
        mvc.perform(delete(path() + "/" + publicId).session(manager).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(delete(path() + "/" + publicId).session(session("ADMIN")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void roleAndRelationshipRevocationAffectExistingSessionsAndPreviouslyDeletedRows()
            throws Exception {
        var manager = session("MANAGER");
        var admin = session("ADMIN");
        mvc.perform(delete(path() + "/" + privateId).session(manager).with(csrf()))
                .andExpect(status().isNoContent());
        jdbc.update("DELETE FROM club_managers WHERE user_id=?", users.get("MANAGER"));
        jdbc.update("UPDATE users SET role='USER' WHERE id=?", users.get("ADMIN"));
        for (var actor : List.of(manager, admin))
            for (long id : List.of(publicId, privateId))
                mvc.perform(delete(path() + "/" + id).session(actor).with(csrf()))
                        .andExpect(status().isForbidden());
        mvc.perform(delete(announcementPath()).session(admin).with(csrf()))
                .andExpect(status().isForbidden());
        assertThat(notices.findById(publicId).orElseThrow().getDeletedAt()).isNull();
        assertThat(announcements.findById(announcementId).orElseThrow().getDeletedAt()).isNull();
    }

    @Test
    void nonexistentTargetsWrongClubAndInvalidIdsDoNotChangeRows() throws Exception {
        var admin = session("ADMIN");
        for (String url :
                List.of(
                        path() + "/9223372036854775807",
                        "/api/v1/clubs/" + otherClub + "/notices/" + publicId,
                        "/api/v1/clubs/9223372036854775807/notices/" + publicId,
                        "/api/v1/announcements/9223372036854775807"))
            mvc.perform(delete(url).session(admin).with(csrf())).andExpect(status().isNotFound());
        for (String url :
                List.of(
                        path() + "/0",
                        path() + "/-1",
                        "/api/v1/clubs/0/notices/" + publicId,
                        "/api/v1/announcements/0",
                        "/api/v1/announcements/invalid"))
            mvc.perform(delete(url).session(admin).with(csrf())).andExpect(status().isBadRequest());
        assertThat(notices.findById(publicId).orElseThrow().getDeletedAt()).isNull();
        assertThat(announcements.findById(announcementId).orElseThrow().getDeletedAt()).isNull();
    }

    @Test
    void authenticationAndCsrfAreRequiredForBothApis() throws Exception {
        for (String url : List.of(path() + "/" + publicId, announcementPath())) {
            mvc.perform(delete(url).with(csrf())).andExpect(status().isUnauthorized());
            mvc.perform(delete(url).session(session("ADMIN"))).andExpect(status().isForbidden());
            mvc.perform(delete(url).session(session("ADMIN")).with(csrf().useInvalidToken()))
                    .andExpect(status().isForbidden());
        }
        assertThat(notices.findById(publicId).orElseThrow().getDeletedAt()).isNull();
        assertThat(announcements.findById(announcementId).orElseThrow().getDeletedAt()).isNull();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void concurrentDeletesCompleteAndKeepOneDeletionTimestamp(boolean operating) throws Exception {
        var a = CompletableFuture.runAsync(() -> deleteDirectly(operating, "ADMIN"));
        var b = CompletableFuture.runAsync(() -> deleteDirectly(operating, "SECOND_ADMIN"));
        CompletableFuture.allOf(a, b).get(15, TimeUnit.SECONDS);
        Instant first =
                operating
                        ? announcements.findById(announcementId).orElseThrow().getDeletedAt()
                        : notices.findById(publicId).orElseThrow().getDeletedAt();
        assertThat(first).isAfter(createdAt);
        deleteDirectly(operating, "ADMIN");
        assertThat(
                        operating
                                ? announcements
                                        .findById(announcementId)
                                        .orElseThrow()
                                        .getDeletedAt()
                                : notices.findById(publicId).orElseThrow().getDeletedAt())
                .isEqualTo(first);
    }

    void deleteDirectly(boolean operating, String role) {
        if (operating) announcementService.delete(users.get(role), announcementId);
        else noticeService.delete(users.get(role), club, publicId);
    }
}
