package com.clubplatform.club;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.clubplatform.club.entity.Club;
import com.clubplatform.club.entity.OperationStatus;
import com.clubplatform.club.repository.ClubRepository;
import com.clubplatform.support.PostgreSqlTestConfig;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 동아리 공개 조회 계약·모집 계산·JPA 매핑·DB 제약을 통합 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgreSqlTestConfig.class, ClubQueryIntegrationTests.FixedTimeConfig.class})
@Transactional
class ClubQueryIntegrationTests {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 12);

    @Autowired private MockMvc mockMvc;

    @Autowired private JdbcTemplate jdbcTemplate;

    @Autowired private ClubRepository clubRepository;

    @Autowired private EntityManager entityManager;

    /** 기본 목록의 아카이브 제외·ID 정렬·페이지 응답을 검증한다. */
    @Test
    void listExcludesArchivedClubsAndReturnsStablePagination() throws Exception {
        long activeId = insertClub("Active", OperationStatus.ACTIVE);
        long dormantId = insertClub("Dormant", OperationStatus.DORMANT);
        insertClub("Archived", OperationStatus.ARCHIVED);

        mockMvc.perform(get("/api/v1/clubs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(activeId))
                .andExpect(jsonPath("$.content[1].id").value(dormantId))
                .andExpect(jsonPath("$.content[0].recruitmentStatus").value("NOT_RECRUITING"))
                .andExpect(jsonPath("$.content[0].description").doesNotExist())
                .andExpect(jsonPath("$.content[0].createdAt").doesNotExist())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1));

        mockMvc.perform(get("/api/v1/clubs").param("page", "1").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(dormantId))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    /** 각 운영 상태 필터가 해당 동아리만 반환하는지 검증한다. */
    @ParameterizedTest
    @EnumSource(OperationStatus.class)
    void listFiltersByEachOperationStatus(OperationStatus operationStatus) throws Exception {
        long expectedId = 0;
        for (OperationStatus candidate : OperationStatus.values()) {
            long id = insertClub(candidate.name(), candidate);
            if (candidate == operationStatus) {
                expectedId = id;
            }
        }

        mockMvc.perform(get("/api/v1/clubs").param("operationStatus", operationStatus.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(expectedId))
                .andExpect(jsonPath("$.content[0].operationStatus").value(operationStatus.name()))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    /** 동아리가 없을 때 빈 목록과 전체 개수 응답을 검증한다. */
    @Test
    void listReturnsEmptyPageWhenThereAreNoClubs() throws Exception {
        mockMvc.perform(get("/api/v1/clubs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    /** 마지막 페이지를 넘는 요청이 빈 항목과 전체 개수를 반환하는지 검증한다. */
    @Test
    void listReturnsEmptyContentForPageBeyondLastPage() throws Exception {
        insertClub("Active", OperationStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/clubs").param("page", "3").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page").value(3))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    /** 한국 날짜 경계에서 공개 상세 필드와 당일 모집 상태를 검증한다. */
    @Test
    void detailReturnsPublicFieldsAndRecruitmentAtSeoulDateBoundary() throws Exception {
        long id = insertClub("Coding Club", OperationStatus.ACTIVE);
        configureRecruitment(id, TODAY, TODAY, false);

        mockMvc.perform(get("/api/v1/clubs/{clubId}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Coding Club"))
                .andExpect(jsonPath("$.summary").value("Club summary"))
                .andExpect(jsonPath("$.description").value("Club description"))
                .andExpect(jsonPath("$.operationStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.recruitment.planned").value(true))
                .andExpect(jsonPath("$.recruitment.status").value("OPEN"))
                .andExpect(jsonPath("$.recruitment.startDate").value("2026-10-12"))
                .andExpect(jsonPath("$.recruitment.endDate").value("2026-10-12"))
                .andExpect(jsonPath("$.recruitment.closedEarly").value(false))
                .andExpect(jsonPath("$.recruitment.applicationMethod").value("Apply via link"))
                .andExpect(
                        jsonPath("$.recruitment.applicationUrl").value("https://example.com/apply"))
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.updatedAt").isString())
                .andExpect(jsonPath("$.managers").doesNotExist())
                .andExpect(jsonPath("$.activityLevel").doesNotExist());
    }

    /** 아카이브된 동아리의 상세 정보도 공개 조회할 수 있는지 검증한다. */
    @Test
    void archivedClubRemainsAccessibleById() throws Exception {
        long id = insertClub("Archived", OperationStatus.ARCHIVED);

        mockMvc.perform(get("/api/v1/clubs/{clubId}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operationStatus").value("ARCHIVED"))
                .andExpect(jsonPath("$.recruitment.status").value("NOT_RECRUITING"));
    }

    /** 모집 일정·조기 마감·운영 상태에 따른 목록과 상세의 모집 상태를 검증한다. */
    @ParameterizedTest
    @MethodSource("recruitmentCases")
    void detailCalculatesRecruitmentStatus(
            OperationStatus operationStatus,
            LocalDate start,
            LocalDate end,
            boolean closedEarly,
            String expected)
            throws Exception {
        long id = insertClub("Recruiting", operationStatus);
        configureRecruitment(id, start, end, closedEarly);

        mockMvc.perform(get("/api/v1/clubs/{clubId}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recruitment.status").value(expected));

        mockMvc.perform(get("/api/v1/clubs").param("operationStatus", operationStatus.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].recruitmentStatus").value(expected));
    }

    /** 모집 날짜 경계와 운영 상태별 예상 결과를 테스트 인자로 제공한다. */
    static Stream<Arguments> recruitmentCases() {
        return Stream.of(
                Arguments.of(OperationStatus.ACTIVE, TODAY.plusDays(1), null, false, "SCHEDULED"),
                Arguments.of(OperationStatus.ACTIVE, TODAY, null, false, "OPEN"),
                Arguments.of(OperationStatus.ACTIVE, TODAY.minusDays(1), TODAY, false, "OPEN"),
                Arguments.of(
                        OperationStatus.ACTIVE,
                        TODAY.minusDays(2),
                        TODAY.minusDays(1),
                        false,
                        "CLOSED"),
                Arguments.of(OperationStatus.ACTIVE, TODAY.plusDays(1), null, true, "CLOSED"),
                Arguments.of(OperationStatus.DORMANT, TODAY, null, false, "CLOSED"),
                Arguments.of(OperationStatus.ARCHIVED, TODAY, null, false, "CLOSED"));
    }

    /** 없는 동아리 조회의 404 상태와 공통 오류 본문을 검증한다. */
    @Test
    void missingClubReturnsConsistentNotFoundResponse() throws Exception {
        mockMvc.perform(get("/api/v1/clubs/{clubId}", Long.MAX_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("동아리를 찾을 수 없습니다."));
    }

    /** 잘못된 조회 매개변수의 400 상태와 공통 오류 본문을 검증한다. */
    @ParameterizedTest
    @ValueSource(
            strings = {
                "/api/v1/clubs?page=-1",
                "/api/v1/clubs?page=abc",
                "/api/v1/clubs?page=2147483648",
                "/api/v1/clubs?size=0",
                "/api/v1/clubs?size=-1",
                "/api/v1/clubs?size=101",
                "/api/v1/clubs?size=abc",
                "/api/v1/clubs?operationStatus=UNKNOWN",
                "/api/v1/clubs/0",
                "/api/v1/clubs/-1",
                "/api/v1/clubs/abc"
            })
    void invalidParametersReturnConsistentBadRequestResponse(String uri) throws Exception {
        mockMvc.perform(get(uri))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    /** 동아리의 JPA 저장·재조회와 기본 상태·시각을 검증한다. */
    @Test
    void persistsClubThroughJpaWithDefaultStateAndTimestamps() {
        Club saved = clubRepository.saveAndFlush(new Club("Coding Club", "Summary", "Description"));
        entityManager.clear();

        Club loaded = clubRepository.findById(saved.getId()).orElseThrow();

        assertThat(loaded.getOperationStatus()).isEqualTo(OperationStatus.ACTIVE);
        assertThat(loaded.recruitmentStatusOn(TODAY)).hasToString("NOT_RECRUITING");
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isEqualTo(loaded.getCreatedAt());
    }

    /** DB가 잘못된 운영 상태와 모집 데이터 조합을 거부하는지 검증한다. */
    @ParameterizedTest
    @ValueSource(
            strings = {
                "operation_status = 'UNKNOWN'",
                "recruitment_planned = true",
                "recruitment_planned = true, recruitment_start_date = DATE '2026-10-12'",
                "recruitment_end_date = DATE '2026-10-12'",
                "recruitment_start_date = DATE '2026-10-12', recruitment_end_date = DATE '2026-10-11'",
                "recruitment_closed_early = true"
            })
    void databaseRejectsInvalidClubState(String invalidAssignment) {
        long id = insertClub("Invalid state", OperationStatus.ACTIVE);

        assertThatThrownBy(
                        () ->
                                jdbcTemplate.update(
                                        "UPDATE clubs SET " + invalidAssignment + " WHERE id = ?",
                                        id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** 조회 테스트용 동아리를 삽입하고 생성된 ID를 반환한다. */
    private long insertClub(String name, OperationStatus operationStatus) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO clubs (name, summary, description, operation_status, created_at, updated_at)
                VALUES (?, 'Club summary', 'Club description', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                RETURNING id
                """,
                Long.class,
                name,
                operationStatus.name());
    }

    /** 조회 테스트에 필요한 모집 일정과 지원 안내를 설정한다. */
    private void configureRecruitment(
            long id, LocalDate start, LocalDate end, boolean closedEarly) {
        jdbcTemplate.update(
                """
                UPDATE clubs
                SET recruitment_planned = true, recruitment_start_date = ?, recruitment_end_date = ?,
                    recruitment_closed_early = ?, application_method = 'Apply via link',
                    application_url = 'https://example.com/apply'
                WHERE id = ?
                """,
                start,
                end,
                closedEarly,
                id);
    }

    /** 모집 날짜 경계 테스트에 사용할 고정 Clock을 등록한다. */
    @TestConfiguration(proxyBeanMethods = false)
    static class FixedTimeConfig {

        /** UTC와 한국 날짜가 달라지는 시점의 고정 Clock을 제공한다. */
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-10-11T15:00:00Z"), ZoneId.of("Asia/Seoul"));
        }
    }
}
