package com.clubplatform.mvp;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.clubplatform.activity.service.*;
import com.clubplatform.activity.storage.*;
import com.clubplatform.club.entity.Club;
import com.clubplatform.club.repository.ClubRepository;
import com.clubplatform.gathering.service.SessionParticipationService;
import com.clubplatform.global.exception.ConflictException;
import com.clubplatform.global.security.AuthenticatedUser;
import com.clubplatform.support.PostgreSqlTestConfig;
import com.clubplatform.user.service.UserAccountService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgreSqlTestConfig.class, EventIntegrationTests.Time.class})
class EventIntegrationTests {
    static final Instant NOW = Instant.parse("2030-05-01T00:00:00Z");

    static class MutableClock extends Clock {
        volatile Instant value = NOW;

        public Instant instant() {
            return value;
        }

        public ZoneId getZone() {
            return ZoneId.of("Asia/Seoul");
        }

        public Clock withZone(ZoneId zone) {
            return Clock.fixed(value, zone);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Time {
        @Bean
        @Primary
        MutableClock testEventClock() {
            return new MutableClock();
        }
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ClubRepository clubs;
    @Autowired UserAccountService accounts;
    @Autowired ObjectMapper mapper;
    @Autowired MutableClock clock;
    @Autowired ActivityParticipationService activityParticipation;
    @Autowired SessionParticipationService sessionParticipation;
    @Autowired PhotoCleanup cleanup;
    @MockitoBean LocalPhotoStorage storage;
    long owner, user, other, admin, club;
    Set<String> files = ConcurrentHashMap.newKeySet();

    @BeforeEach
    void setup() {
        clock.value = NOW;
        clean();
        jdbc.update("DELETE FROM club_managers");
        jdbc.update("DELETE FROM clubs");
        jdbc.update("DELETE FROM users");
        owner = accounts.synchronize42Profile(5001, "owner", "Owner").id();
        user = accounts.synchronize42Profile(5002, "member", "Member").id();
        other = accounts.synchronize42Profile(5003, "other", "Other").id();
        admin = accounts.synchronize42Profile(5004, "admin", "Admin").id();
        jdbc.update("UPDATE users SET role='SUPER_ADMIN' WHERE id=?", admin);
        club = clubs.saveAndFlush(new Club("Club", "Summary", "Description")).getId();
        jdbc.update(
                "INSERT INTO club_managers(user_id,club_id,created_at) VALUES (?,?,now())",
                owner,
                club);
        files.clear();
        doAnswer(
                        i -> {
                            files.add(i.getArgument(0));
                            return null;
                        })
                .when(storage)
                .put(anyString(), any(), anyString());
        doAnswer(
                        i -> {
                            files.remove(i.getArgument(0));
                            return null;
                        })
                .when(storage)
                .delete(anyString());
        when(storage.url(anyString()))
                .thenAnswer(i -> "https://images.example/" + i.getArgument(0));
    }

    @AfterEach
    void clean() {
        jdbc.execute("DROP TRIGGER IF EXISTS test_image_failure ON activity_images");
        jdbc.execute("DROP FUNCTION IF EXISTS test_image_failure()");
        for (String table :
                List.of(
                        "activity_images",
                        "activity_participants",
                        "activities",
                        "gathering_session_participants",
                        "gathering_sessions",
                        "gatherings",
                        "image_uploads",
                        "notices",
                        "club_members")) jdbc.update("DELETE FROM " + table);
    }

    MockHttpSession session(long id) {
        var identity = accounts.findIdentity(id).orElseThrow();
        var ctx = SecurityContextHolder.createEmptyContext();
        var p = new AuthenticatedUser(identity.id(), identity.role());
        ctx.setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(p, null, p.getAuthorities()));
        var s = new MockHttpSession();
        s.setAttribute("SPRING_SECURITY_CONTEXT", ctx);
        return s;
    }

    String activityJson(int capacity) {
        return "{\"title\":\"Event\",\"content\":\"Body\",\"startsAt\":\"2030-05-02T09:00:00+09:00\",\"recruitmentClosesAt\":\"2030-05-02T08:00:00+09:00\",\"capacity\":"
                + capacity
                + "}";
    }

    String sessionJson(int capacity) {
        return "{\"title\":\"Session\",\"startsAt\":\"2030-05-02T09:00:00+09:00\",\"endsAt\":\"2030-05-02T11:00:00+09:00\",\"recruitmentClosesAt\":\"2030-05-02T08:00:00+09:00\",\"location\":\"Campus\",\"capacity\":"
                + capacity
                + "}";
    }

    MockMultipartFile activityPart(int capacity) {
        return new MockMultipartFile(
                "activity",
                "",
                "application/json",
                activityJson(capacity).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    MockMultipartFile photo() throws Exception {
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", bytes);
        return new MockMultipartFile("images", "untrusted.exe", "text/plain", bytes.toByteArray());
    }

    long create(boolean gathering, int capacity) throws Exception {
        if (gathering) {
            var response =
                    mvc.perform(
                                    post("/api/v1/gatherings")
                                            .session(session(owner))
                                            .with(csrf())
                                            .contentType("application/json")
                                            .content(
                                                    "{\"title\":\"Gathering\",\"content\":\"Body\",\"session\":"
                                                            + sessionJson(capacity)
                                                            + "}"))
                            .andExpect(status().isCreated())
                            .andReturn();
            return mapper.readTree(response.getResponse().getContentAsString())
                    .get("sessionId")
                    .asLong();
        }
        var response =
                mvc.perform(
                                multipart("/api/v1/clubs/" + club + "/activities")
                                        .file(activityPart(capacity))
                                        .session(session(owner))
                                        .with(csrf()))
                        .andExpect(status().isCreated())
                        .andReturn();
        long id = mapper.readTree(response.getResponse().getContentAsString()).get("id").asLong();
        assertThat(response.getResponse().getHeader("Location"))
                .isEqualTo("/api/v1/clubs/" + club + "/activities/" + id);
        return id;
    }

    String base(boolean gathering, long id) {
        long gatheringId =
                gathering
                        ? jdbc.queryForObject(
                                "SELECT gathering_id FROM gathering_sessions WHERE id=?",
                                Long.class,
                                id)
                        : 0;
        return "/api/v1/"
                + (gathering
                        ? "gatherings/" + gatheringId + "/sessions/"
                        : "clubs/" + club + "/activities/")
                + id;
    }

    String table(boolean gathering) {
        return gathering ? "gathering_session_participants" : "activity_participants";
    }

    String fk(boolean gathering) {
        return gathering ? "session_id" : "activity_id";
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void publicReadsAutoOrganizerAndCapacityOne(boolean gathering) throws Exception {
        long id = create(gathering, 1);
        String uri = base(gathering, id);
        mvc.perform(get(uri))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentParticipantCount").value(1))
                .andExpect(jsonPath("$.remainingCapacity").value(0))
                .andExpect(jsonPath("$.recruiting").value(false))
                .andExpect(jsonPath("$.participants").doesNotExist());
        mvc.perform(get(uri + "/participants/me").session(session(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").isEmpty());
        mvc.perform(post(uri + "/participants").session(session(user)).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CAPACITY_FULL"));
        mvc.perform(delete(uri + "/participants/me").session(session(owner)).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORGANIZER_CANNOT_CANCEL"));
        mvc.perform(get(uri + "/participants")).andExpect(status().isUnauthorized());
        mvc.perform(post(uri + "/participants").session(session(user)))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void participationCancellationRejoinAndAttendancePermissions(boolean gathering)
            throws Exception {
        long id = create(gathering, 3);
        String uri = base(gathering, id);
        mvc.perform(post(uri + "/participants").session(session(user)).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(post(uri + "/participants").session(session(user)).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_JOINED"));
        mvc.perform(delete(uri + "/participants/me").session(session(user)).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(delete(uri + "/participants/me").session(session(user)).with(csrf()))
                .andExpect(status().isConflict());
        mvc.perform(post(uri + "/participants").session(session(user)).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM "
                                        + table(gathering)
                                        + " WHERE "
                                        + fk(gathering)
                                        + "=? AND user_id=?",
                                Integer.class,
                                id,
                                user))
                .isEqualTo(1);
        mvc.perform(get(uri + "/participants").session(session(admin)))
                .andExpect(status().isForbidden());
        String attendance = uri + "/participants/" + user + "/attendance";
        mvc.perform(
                        patch(attendance)
                                .session(session(other))
                                .with(csrf())
                                .contentType("application/json")
                                .content("{\"attendanceStatus\":\"PRESENT\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(
                        patch(attendance)
                                .session(session(owner))
                                .with(csrf())
                                .contentType("application/json")
                                .content("{\"attendanceStatus\":\"ABSENT\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        patch(attendance)
                                .session(session(owner))
                                .with(csrf())
                                .contentType("application/json")
                                .content("{\"attendanceStatus\":\"PRESENT\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(get(uri + "/participants/me").session(session(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attendanceStatus").value("PRESENT"));
        mvc.perform(delete(uri + "/participants/me").session(session(user)).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ATTENDANCE_RECORDED"));
        mvc.perform(get(uri + "/participants").session(session(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(
                        patch(attendance)
                                .session(session(owner))
                                .with(csrf())
                                .contentType("application/json")
                                .content("{\"attendanceStatus\":\"UNCHECKED\"}"))
                .andExpect(status().isNoContent());
        assertThat(
                        jdbc.queryForMap(
                                "SELECT attendance_checked_by,attendance_checked_at FROM "
                                        + table(gathering)
                                        + " WHERE "
                                        + fk(gathering)
                                        + "=? AND user_id=?",
                                id,
                                user))
                .allSatisfy((k, v) -> assertThat(v).isNull());
        mvc.perform(delete(uri + "/participants/me").session(session(user)).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(
                        patch(attendance)
                                .session(session(owner))
                                .with(csrf())
                                .contentType("application/json")
                                .content("{\"attendanceStatus\":\"PRESENT\"}"))
                .andExpect(status().isConflict());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void exactDeadlineBlocksJoinAndCancel(boolean gathering) throws Exception {
        long id = create(gathering, 3);
        String uri = base(gathering, id);
        clock.value = Instant.parse("2030-05-01T22:59:59.999999Z");
        mvc.perform(post(uri + "/participants").session(session(user)).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(delete(uri + "/participants/me").session(session(user)).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(post(uri + "/participants").session(session(user)).with(csrf()))
                .andExpect(status().isNoContent());
        clock.value = Instant.parse("2030-05-01T23:00:00Z");
        mvc.perform(post(uri + "/participants").session(session(other)).with(csrf()))
                .andExpect(status().isConflict());
        mvc.perform(delete(uri + "/participants/me").session(session(user)).with(csrf()))
                .andExpect(status().isConflict());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void lastSeatConcurrentRequestsNeverOverbook(boolean gathering) throws Exception {
        long id = create(gathering, 2);
        var gate = new CyclicBarrier(2);
        var success = new AtomicInteger();
        var rejected = new AtomicInteger();
        var tasks = new ArrayList<CompletableFuture<Void>>();
        for (long actor : List.of(user, other))
            tasks.add(
                    CompletableFuture.runAsync(
                            () -> {
                                try {
                                    gate.await(10, TimeUnit.SECONDS);
                                    if (gathering)
                                        sessionParticipation.join(
                                                actor,
                                                jdbc.queryForObject(
                                                        "SELECT gathering_id FROM gathering_sessions WHERE id=?",
                                                        Long.class,
                                                        id),
                                                id);
                                    else activityParticipation.join(actor, club, id);
                                    success.incrementAndGet();
                                } catch (ConflictException e) {
                                    assertThat(e.getCode()).isEqualTo("CAPACITY_FULL");
                                    rejected.incrementAndGet();
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            }));
        CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).get(20, TimeUnit.SECONDS);
        assertThat(success.get()).isEqualTo(1);
        assertThat(rejected.get()).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM "
                                        + table(gathering)
                                        + " WHERE "
                                        + fk(gathering)
                                        + "=? AND status='CONFIRMED'",
                                Integer.class,
                                id))
                .isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void deletedParentBlocksAllReadsAndWrites(boolean gathering) throws Exception {
        long id = create(gathering, 3);
        String uri = base(gathering, id);
        if (gathering)
            jdbc.update(
                    "UPDATE gatherings SET deleted_at=now() WHERE id=(SELECT gathering_id FROM gathering_sessions WHERE id=?)",
                    id);
        else jdbc.update("UPDATE activities SET deleted_at=now() WHERE id=?", id);
        mvc.perform(get(uri)).andExpect(status().isNotFound());
        mvc.perform(get(uri + "/participants/me").session(session(user)))
                .andExpect(status().isNotFound());
        mvc.perform(get(uri + "/participants").session(session(owner)))
                .andExpect(status().isNotFound());
        mvc.perform(post(uri + "/participants").session(session(user)).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(
                        patch(uri + "/participants/" + owner + "/attendance")
                                .session(session(owner))
                                .with(csrf())
                                .contentType("application/json")
                                .content("{\"attendanceStatus\":\"PRESENT\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void sessionTitlesAreRequiredAndReturnedForFirstAndFollowingSessions() throws Exception {
        long first = create(true, 3);
        long gathering =
                jdbc.queryForObject(
                        "SELECT gathering_id FROM gathering_sessions WHERE id=?",
                        Long.class,
                        first);
        String path = "/api/v1/gatherings/" + gathering + "/sessions";
        mvc.perform(get(path + "/" + first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Session"));
        mvc.perform(get("/api/v1/gatherings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].representativeSession.title").value("Session"));
        mvc.perform(
                        post(path)
                                .session(session(owner))
                                .with(csrf())
                                .contentType("application/json")
                                .content(
                                        sessionJson(3)
                                                .replace("Session", "October book discussion")))
                .andExpect(status().isCreated());
        mvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[1].title").value("October book discussion"));
        for (String invalid :
                List.of(
                        sessionJson(3).replace("\"title\":\"Session\",", ""),
                        sessionJson(3).replace("Session", "   "),
                        sessionJson(3).replace("Session", "x".repeat(201)))) {
            mvc.perform(
                            post(path)
                                    .session(session(owner))
                                    .with(csrf())
                                    .contentType("application/json")
                                    .content(invalid))
                    .andExpect(status().isBadRequest());
            mvc.perform(
                            post("/api/v1/gatherings")
                                    .session(session(owner))
                                    .with(csrf())
                                    .contentType("application/json")
                                    .content(
                                            "{\"title\":\"Gathering\",\"content\":\"Body\",\"session\":"
                                                    + invalid
                                                    + "}"))
                    .andExpect(status().isBadRequest());
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM gathering_sessions", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void nextSessionIsIndependentAndOnlyOrganizerCanCreate() throws Exception {
        long first = create(true, 3);
        long gathering =
                jdbc.queryForObject(
                        "SELECT gathering_id FROM gathering_sessions WHERE id=?",
                        Long.class,
                        first);
        String path = "/api/v1/gatherings/" + gathering + "/sessions";
        mvc.perform(
                        post(path)
                                .session(session(other))
                                .with(csrf())
                                .contentType("application/json")
                                .content(sessionJson(3)))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post(path)
                                .session(session(owner))
                                .with(csrf())
                                .contentType("application/json")
                                .content(sessionJson(3)))
                .andExpect(status().isCreated())
                .andExpect(
                        header().string(
                                        "Location",
                                        path
                                                + "/"
                                                + jdbc.queryForObject(
                                                        "SELECT max(id) FROM gathering_sessions",
                                                        Long.class)));
        long second = jdbc.queryForObject("SELECT max(id) FROM gathering_sessions", Long.class);
        assertThat(second).isNotEqualTo(first);
        for (long id : List.of(first, second))
            mvc.perform(post(base(true, id) + "/participants").session(session(user)).with(csrf()))
                    .andExpect(status().isNoContent());
        mvc.perform(
                        delete(base(true, first) + "/participants/me")
                                .session(session(user))
                                .with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get(base(true, second) + "/participants/me").session(session(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        mvc.perform(get(path).param("filter", "UPCOMING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        clock.value = Instant.parse("2030-05-02T00:00:00Z");
        mvc.perform(get(path).param("filter", "ONGOING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        clock.value = Instant.parse("2030-05-02T02:00:00Z");
        mvc.perform(get(path).param("filter", "PAST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void publicListsSortRecruitingBeforeFullAndPaginate() throws Exception {
        long full = create(false, 1), open = create(false, 2);
        String path = "/api/v1/clubs/" + club + "/activities";
        mvc.perform(get(path).param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(open))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get(path).param("size", "1").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(full));
        create(true, 1);
        long openSession = create(true, 2);
        mvc.perform(get("/api/v1/gatherings").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.content[0].representativeSession.sessionId").value(openSession))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void activityPhotosValidateOrderRepresentativeAndNoAttachment() throws Exception {
        long noPhoto = create(false, 1);
        mvc.perform(get(base(false, noPhoto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.images").isEmpty())
                .andExpect(jsonPath("$.representativeImageUrl").isEmpty());
        var response =
                mvc.perform(
                                multipart("/api/v1/clubs/" + club + "/activities")
                                        .file(activityPart(3))
                                        .file(photo())
                                        .file(photo())
                                        .session(session(owner))
                                        .with(csrf()))
                        .andExpect(status().isCreated())
                        .andReturn();
        long id = mapper.readTree(response.getResponse().getContentAsString()).get("id").asLong();
        var detail =
                mvc.perform(get(base(false, id)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.images.length()").value(2))
                        .andExpect(jsonPath("$.images[0].sortOrder").value(0))
                        .andReturn();
        var json = mapper.readTree(detail.getResponse().getContentAsString());
        assertThat(json.get("representativeImageUrl"))
                .isEqualTo(json.get("images").get(0).get("url"));
        assertThat(files).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM image_uploads", Integer.class))
                .isZero();
    }

    @Test
    void corruptTooManyAndOversizedPhotosAreRejectedBeforeUpload() throws Exception {
        String path = "/api/v1/clubs/" + club + "/activities";
        mvc.perform(
                        multipart(path)
                                .file(activityPart(3))
                                .file(
                                        new MockMultipartFile(
                                                "images",
                                                "fake.png",
                                                "image/png",
                                                new byte[] {1, 2, 3}))
                                .session(session(owner))
                                .with(csrf()))
                .andExpect(status().isBadRequest());
        var request = multipart(path).file(activityPart(3));
        for (int i = 0; i < 6; i++) request.file(photo());
        mvc.perform(request.session(session(owner)).with(csrf()))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        multipart(path)
                                .file(activityPart(3))
                                .file(
                                        new MockMultipartFile(
                                                "images",
                                                "large.png",
                                                "image/png",
                                                new byte[5242881]))
                                .session(session(owner))
                                .with(csrf()))
                .andExpect(status().isPayloadTooLarge());
        mvc.perform(
                        multipart(path)
                                .file(activityPart(3))
                                .file(new MockMultipartFile("images", new byte[0]))
                                .session(session(owner))
                                .with(csrf()))
                .andExpect(status().isBadRequest());
        verify(storage, never()).put(anyString(), any(), anyString());
    }

    @Test
    void uploadFailureCleansEvenAmbiguousFailedPutAndRetriesDelete() throws Exception {
        var calls = new AtomicInteger();
        doAnswer(
                        i -> {
                            files.add(i.getArgument(0));
                            if (calls.incrementAndGet() == 2)
                                throw new StorageUnavailableException("test upload failure");
                            return null;
                        })
                .when(storage)
                .put(anyString(), any(), anyString());
        doThrow(new StorageUnavailableException("test delete failure"))
                .when(storage)
                .delete(anyString());
        mvc.perform(
                        multipart("/api/v1/clubs/" + club + "/activities")
                                .file(activityPart(3))
                                .file(photo())
                                .file(photo())
                                .session(session(owner))
                                .with(csrf()))
                .andExpect(status().isServiceUnavailable());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM activities", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM image_uploads", Integer.class))
                .isEqualTo(2);
        doAnswer(
                        i -> {
                            files.remove(i.getArgument(0));
                            return null;
                        })
                .when(storage)
                .delete(anyString());
        clock.value = NOW.plusSeconds(601);
        cleanup.retry();
        assertThat(files).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM image_uploads", Integer.class))
                .isZero();
    }

    @Test
    void databaseFailureRollsBackActivityOrganizerAndPhotos() throws Exception {
        jdbc.execute(
                "CREATE FUNCTION test_image_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'injected image insert failure'; END $$");
        jdbc.execute(
                "CREATE TRIGGER test_image_failure BEFORE INSERT ON activity_images FOR EACH ROW EXECUTE FUNCTION test_image_failure()");
        assertThatThrownBy(
                        () ->
                                mvc.perform(
                                        multipart("/api/v1/clubs/" + club + "/activities")
                                                .file(activityPart(3))
                                                .file(photo())
                                                .session(session(owner))
                                                .with(csrf())))
                .hasRootCauseInstanceOf(java.sql.SQLException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM activities", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM activity_participants", Integer.class))
                .isZero();
        assertThat(files).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM image_uploads", Integer.class))
                .isZero();
    }

    @Test
    void invalidTimesAndOtherClubManagerCannotCreate() throws Exception {
        long otherClub = clubs.saveAndFlush(new Club("Other", "Summary", "Description")).getId();
        mvc.perform(
                        multipart("/api/v1/clubs/" + otherClub + "/activities")
                                .file(activityPart(3))
                                .session(session(owner))
                                .with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(
                        multipart("/api/v1/clubs/" + club + "/activities")
                                .file(activityPart(3))
                                .session(session(admin))
                                .with(csrf()))
                .andExpect(status().isForbidden());
        String invalid =
                activityJson(3).replace("2030-05-02T08:00:00+09:00", "2030-05-02T09:00:00+09:00");
        mvc.perform(
                        multipart("/api/v1/clubs/" + club + "/activities")
                                .file(
                                        new MockMultipartFile(
                                                "activity",
                                                "",
                                                "application/json",
                                                invalid.getBytes()))
                                .session(session(owner))
                                .with(csrf()))
                .andExpect(status().isBadRequest());
        String tooSoon =
                sessionJson(3).replace("2030-05-02T09:00:00+09:00", "2030-05-01T10:00:00+09:00");
        mvc.perform(
                        post("/api/v1/gatherings")
                                .session(session(owner))
                                .with(csrf())
                                .contentType("application/json")
                                .content(
                                        "{\"title\":\"Test\",\"content\":\"Body\",\"session\":"
                                                + tooSoon
                                                + "}"))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM gatherings", Integer.class)).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void concurrentDuplicateParticipantStaysOneAndRecordedCancelledCannotRejoin(boolean gathering)
            throws Exception {
        long id = create(gathering, 3);
        var gate = new CyclicBarrier(2);
        var success = new AtomicInteger();
        var conflicts = new AtomicInteger();
        var tasks = new ArrayList<CompletableFuture<Void>>();
        for (int n = 0; n < 2; n++)
            tasks.add(
                    CompletableFuture.runAsync(
                            () -> {
                                try {
                                    gate.await(10, TimeUnit.SECONDS);
                                    if (gathering)
                                        sessionParticipation.join(
                                                user,
                                                jdbc.queryForObject(
                                                        "SELECT gathering_id FROM gathering_sessions WHERE id=?",
                                                        Long.class,
                                                        id),
                                                id);
                                    else activityParticipation.join(user, club, id);
                                    success.incrementAndGet();
                                } catch (ConflictException e) {
                                    assertThat(e.getCode()).isEqualTo("ALREADY_JOINED");
                                    conflicts.incrementAndGet();
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            }));
        CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).get(20, TimeUnit.SECONDS);
        assertThat(success.get()).isEqualTo(1);
        assertThat(conflicts.get()).isEqualTo(1);
        jdbc.update(
                "UPDATE "
                        + table(gathering)
                        + " SET status='CANCELLED',attendance_status='PRESENT',attendance_checked_by=?,attendance_checked_at=? WHERE "
                        + fk(gathering)
                        + "=? AND user_id=?",
                owner,
                java.sql.Timestamp.from(NOW),
                id,
                user);
        mvc.perform(post(base(gathering, id) + "/participants").session(session(user)).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ATTENDANCE_RECORDED"));
    }

    @Test
    void committedImageIsNeverRemovedByCleanupAndFivePhotosAreAllowed() throws Exception {
        var request = multipart("/api/v1/clubs/" + club + "/activities").file(activityPart(3));
        for (int n = 0; n < 5; n++) request.file(photo());
        mvc.perform(request.session(session(owner)).with(csrf())).andExpect(status().isCreated());
        String key =
                jdbc.queryForObject(
                        "SELECT storage_key FROM activity_images ORDER BY sort_order LIMIT 1",
                        String.class);
        cleanup.remove(key);
        assertThat(files).hasSize(5);
        assertThat(files).contains(key);
    }

    @Test
    void sessionParticipantFailureRollsBackNewGatheringAndNewSession() throws Exception {
        jdbc.execute(
                "CREATE FUNCTION test_session_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'injected participant failure'; END $$");
        jdbc.execute(
                "CREATE TRIGGER test_session_failure BEFORE INSERT ON gathering_session_participants FOR EACH ROW EXECUTE FUNCTION test_session_failure()");
        try {
            assertThatThrownBy(() -> create(true, 3))
                    .hasRootCauseInstanceOf(java.sql.SQLException.class);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM gatherings", Integer.class))
                    .isZero();
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT count(*) FROM gathering_sessions", Integer.class))
                    .isZero();
        } finally {
            jdbc.execute("DROP TRIGGER test_session_failure ON gathering_session_participants");
            jdbc.execute("DROP FUNCTION test_session_failure()");
        }
    }

    @Test
    void activityRoutesRequireMatchingClubBeforeAnyParticipantReadOrMutation() throws Exception {
        long id = create(false, 3);
        long wrongClub = clubs.saveAndFlush(new Club("Wrong", "Summary", "Description")).getId();
        String correct = base(false, id),
                wrong = "/api/v1/clubs/" + wrongClub + "/activities/" + id;
        mvc.perform(post(correct + "/participants").session(session(user)).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get(wrong)).andExpect(status().isNotFound());
        mvc.perform(get(wrong + "/participants/me").session(session(user)))
                .andExpect(status().isNotFound());
        mvc.perform(get(wrong + "/participants").session(session(owner)))
                .andExpect(status().isNotFound());
        mvc.perform(post(wrong + "/participants").session(session(other)).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(delete(wrong + "/participants/me").session(session(user)).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(
                        patch(wrong + "/participants/" + user + "/attendance")
                                .session(session(owner))
                                .with(csrf())
                                .contentType("application/json")
                                .content("{\"attendanceStatus\":\"PRESENT\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(get(correct + "/participants/me").session(session(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.attendanceStatus").value("UNCHECKED"));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM activity_participants WHERE activity_id=?",
                                Integer.class,
                                id))
                .isEqualTo(2);
        for (String suffix : List.of("", "/participants", "/participants/me"))
            mvc.perform(get("/api/v1/activities/" + id + suffix).session(session(owner)))
                    .andExpect(status().isNotFound());
        mvc.perform(
                        post("/api/v1/activities/" + id + "/participants")
                                .session(session(other))
                                .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void sessionRoutesRequireMatchingGatheringBeforeAnyParticipantReadOrMutation()
            throws Exception {
        long id = create(true, 3);
        long otherSession = create(true, 3);
        long wrongGathering =
                jdbc.queryForObject(
                        "SELECT gathering_id FROM gathering_sessions WHERE id=?",
                        Long.class,
                        otherSession);
        String correct = base(true, id),
                wrong = "/api/v1/gatherings/" + wrongGathering + "/sessions/" + id;
        mvc.perform(post(correct + "/participants").session(session(user)).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get(wrong)).andExpect(status().isNotFound());
        mvc.perform(get(wrong + "/participants/me").session(session(user)))
                .andExpect(status().isNotFound());
        mvc.perform(get(wrong + "/participants").session(session(owner)))
                .andExpect(status().isNotFound());
        mvc.perform(post(wrong + "/participants").session(session(other)).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(delete(wrong + "/participants/me").session(session(user)).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(
                        patch(wrong + "/participants/" + user + "/attendance")
                                .session(session(owner))
                                .with(csrf())
                                .contentType("application/json")
                                .content("{\"attendanceStatus\":\"PRESENT\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(get(correct + "/participants/me").session(session(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.attendanceStatus").value("UNCHECKED"));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM gathering_session_participants WHERE session_id=?",
                                Integer.class,
                                id))
                .isEqualTo(2);
        for (String suffix : List.of("", "/participants", "/participants/me"))
            mvc.perform(get("/api/v1/gathering-sessions/" + id + suffix).session(session(owner)))
                    .andExpect(status().isNotFound());
        mvc.perform(
                        post("/api/v1/gathering-sessions/" + id + "/participants")
                                .session(session(other))
                                .with(csrf()))
                .andExpect(status().isNotFound());
    }
}
