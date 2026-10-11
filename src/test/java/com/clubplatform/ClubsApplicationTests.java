package com.clubplatform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.clubplatform.support.PostgreSqlTestConfig;
import com.clubplatform.user.entity.User;
import com.clubplatform.user.entity.UserRole;
import jakarta.persistence.EntityManager;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL에서 애플리케이션 초기화·사용자 매핑·DB 제약을 검증한다. */
@SpringBootTest
@Import(PostgreSqlTestConfig.class)
class ClubsApplicationTests {

    @Autowired private Flyway flyway;

    @Autowired private EntityManager entityManager;

    @Autowired private JdbcTemplate jdbcTemplate;

    /** 초기 마이그레이션 적용과 스키마 검증이 완료된 컨텍스트를 확인한다. */
    @Test
    void contextLoadsWithMigratedAndValidatedSchema() {
        assertThat(flyway.info().applied())
                .extracting(migration -> migration.getVersion().getVersion())
                .containsExactly("1", "2", "3");
        assertThat(flyway.info().pending()).isEmpty();
        flyway.validate();
    }

    /** 기존 휴면 데이터를 전환하고 기존 활성·아카이브 및 모집 정보를 보존하는지 검증한다. */
    @Test
    void migratesDormantClubsWithoutReopeningRecruitment() {
        String schema = "club_status_migration_test";
        Flyway legacy =
                Flyway.configure()
                        .dataSource(jdbcTemplate.getDataSource())
                        .schemas(schema)
                        .defaultSchema(schema)
                        .target("2")
                        .load();
        try {
            legacy.migrate();
            jdbcTemplate.update(
                    """
                    INSERT INTO club_status_migration_test.clubs
                        (name, summary, description, operation_status, recruitment_planned,
                         recruitment_start_date, application_method, created_at, updated_at)
                    VALUES
                        ('Dormant recruiting', 'Summary', 'Description', 'DORMANT', true,
                         DATE '2026-10-01', 'External application', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                        ('Dormant idle', 'Summary', 'Description', 'DORMANT', false,
                         NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                        ('Active', 'Summary', 'Description', 'ACTIVE', true,
                         DATE '2026-10-01', 'External application', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                        ('Archived', 'Summary', 'Description', 'ARCHIVED', false,
                         NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """);

            Flyway current =
                    Flyway.configure()
                            .configuration(legacy.getConfiguration())
                            .target(MigrationVersion.LATEST)
                            .load();
            current.migrate();
            current.validate();

            assertThat(
                            jdbcTemplate.queryForList(
                                    "SELECT operation_status FROM club_status_migration_test.clubs ORDER BY id",
                                    String.class))
                    .containsExactly("ACTIVE", "ACTIVE", "ACTIVE", "ARCHIVED");
            assertThat(
                            jdbcTemplate.queryForList(
                                    "SELECT recruitment_closed_early FROM club_status_migration_test.clubs ORDER BY id",
                                    Boolean.class))
                    .containsExactly(true, false, false, false);
            assertThat(
                            jdbcTemplate.queryForObject(
                                    "SELECT application_method FROM club_status_migration_test.clubs WHERE name = 'Dormant recruiting'",
                                    String.class))
                    .isEqualTo("External application");
            assertThatThrownBy(
                            () ->
                                    jdbcTemplate.update(
                                            "UPDATE club_status_migration_test.clubs SET operation_status = 'DORMANT' WHERE name = 'Dormant idle'"))
                    .isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            jdbcTemplate.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    /** JPA로 저장·재조회한 사용자의 정보·기본 역할·시각을 검증한다. */
    @Test
    @Transactional
    void persistsAndLoadsUserWithDefaultRoleAndTimestamps() {
        User user = new User(42L, "testuser", "Test User");
        entityManager.persist(user);
        entityManager.flush();
        entityManager.clear();

        User loaded = entityManager.find(User.class, user.getId());

        assertThat(loaded.getId()).isPositive();
        assertThat(loaded.getIntraId()).isEqualTo(42L);
        assertThat(loaded.getIntraLogin()).isEqualTo("testuser");
        assertThat(loaded.getDisplayName()).isEqualTo("Test User");
        assertThat(loaded.getRole()).isEqualTo(UserRole.USER);
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isEqualTo(loaded.getCreatedAt());
    }

    /** DB가 동일한 42 사용자 ID의 중복 등록을 거부하는지 검증한다. */
    @Test
    @Transactional
    void databaseRejectsDuplicateIntraId() {
        insertUser(100L, "USER");
        assertThatThrownBy(() -> insertUser(100L, "USER"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** DB가 허용하지 않은 사용자 역할을 거부하는지 검증한다. */
    @Test
    @Transactional
    void databaseRejectsUnknownRole() {
        assertThatThrownBy(() -> insertUser(101L, "OWNER"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** 사용자 DB 제약 검증을 위한 테스트 데이터를 직접 삽입한다. */
    private void insertUser(long intraId, String role) {
        jdbcTemplate.update(
                """
                INSERT INTO users (intra_id, intra_login, display_name, role, created_at, updated_at)
                VALUES (?, 'testuser', 'Test User', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                intraId,
                role);
    }
}
