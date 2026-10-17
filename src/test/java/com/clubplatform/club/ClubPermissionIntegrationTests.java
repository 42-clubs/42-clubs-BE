package com.clubplatform.club;

import static org.assertj.core.api.Assertions.*;

import com.clubplatform.club.entity.Club;
import com.clubplatform.club.entity.ClubManager;
import com.clubplatform.club.repository.ClubManagerRepository;
import com.clubplatform.club.repository.ClubRepository;
import com.clubplatform.club.service.ClubPermissionService;
import com.clubplatform.global.exception.ResourceNotFoundException;
import com.clubplatform.support.PostgreSqlTestConfig;
import com.clubplatform.user.service.UserAccountService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

@SpringBootTest
@Import(PostgreSqlTestConfig.class)
class ClubPermissionIntegrationTests {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ClubRepository clubs;
    @Autowired private ClubManagerRepository managers;
    @Autowired private ClubPermissionService permissions;
    @Autowired private UserAccountService users;

    @BeforeEach
    void clear() {
        jdbc.update("DELETE FROM club_managers");
        jdbc.update("DELETE FROM clubs");
        jdbc.update("DELETE FROM users");
    }

    @Test
    void onlyCurrentManagerOfActiveTargetClubCanWriteRegardlessOfGlobalRole() {
        long actor = users.synchronize42Profile(100, "chair", "Chair").id();
        long other = users.synchronize42Profile(101, "other", "Other").id();
        var club = clubs.saveAndFlush(new Club("A", "Summary", "Description"));
        var another = clubs.saveAndFlush(new Club("B", "Summary", "Description"));
        var relation = managers.saveAndFlush(new ClubManager(actor, club.getId()));
        for (String role : new String[] {"USER", "ADMIN"}) {
            jdbc.update("UPDATE users SET role=? WHERE id=?", role, actor);
            assertThatCode(() -> permissions.requireManager(actor, club.getId()))
                    .doesNotThrowAnyException();
            assertThatThrownBy(() -> permissions.requireManager(actor, another.getId()))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> permissions.requireManager(other, club.getId()))
                    .isInstanceOf(AccessDeniedException.class);
        }
        jdbc.update("UPDATE clubs SET operation_status='ARCHIVED' WHERE id=?", club.getId());
        assertThatThrownBy(() -> permissions.requireManager(actor, club.getId()))
                .isInstanceOf(AccessDeniedException.class);
        jdbc.update("UPDATE clubs SET operation_status='ACTIVE' WHERE id=?", club.getId());
        managers.deleteById(relation.getId());
        assertThatThrownBy(() -> permissions.requireManager(actor, club.getId()))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(clubs.existsById(club.getId())).isTrue();
        assertThat(users.findIdentity(actor)).isPresent();
        assertThatThrownBy(() -> permissions.requireManager(actor, Long.MAX_VALUE))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void databaseEnforcesUniqueManagerPairsForeignKeysAndRoleAllowlist() {
        long actor = users.synchronize42Profile(100, "chair", "Chair").id();
        var club = clubs.saveAndFlush(new Club("A", "Summary", "Description"));
        managers.saveAndFlush(new ClubManager(actor, club.getId()));
        assertThatThrownBy(() -> managers.saveAndFlush(new ClubManager(actor, club.getId())))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(
                        () -> managers.saveAndFlush(new ClubManager(Long.MAX_VALUE, club.getId())))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> managers.saveAndFlush(new ClubManager(actor, Long.MAX_VALUE)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE users SET role='SUPER_ADMIN'"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE users SET role='OWNER'"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM users WHERE id=?", actor))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM clubs WHERE id=?", club.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void bootstrapTemplateIsRepeatableAndOAuthSynchronizationPreservesAssignedRoles()
            throws Exception {
        String sql = bootstrapSql();
        jdbc.execute(sql);
        jdbc.execute(sql);
        long adminId = jdbc.queryForObject("SELECT id FROM users WHERE intra_id=9001", Long.class);
        long chairId = jdbc.queryForObject("SELECT id FROM users WHERE intra_id=9002", Long.class);
        assertThat(users.synchronize42Profile(9001, "renamed-admin", "New Admin").id())
                .isEqualTo(adminId);
        assertThat(users.findIdentity(adminId).orElseThrow().role()).isEqualTo("ADMIN");
        assertThat(users.synchronize42Profile(9002, "renamed-chair", "New Chair").id())
                .isEqualTo(chairId);
        assertThat(users.findIdentity(chairId).orElseThrow().role()).isEqualTo("USER");
        assertThat(managers.count()).isEqualTo(1);
        assertThat(clubs.count()).isEqualTo(1);
        permissions.requireManager(chairId, clubs.findAll().get(0).getId());
        assertThatThrownBy(
                        () -> permissions.requireManager(adminId, clubs.findAll().get(0).getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void bootstrapFailureRollsBackClubUserAndManagerTogether() throws Exception {
        String sql = bootstrapSql().replace("'Chair'", "repeat('x', 101)");
        try (var connection = jdbc.getDataSource().getConnection();
                var statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.execute(sql))
                    .isInstanceOf(java.sql.SQLException.class);
            statement.execute("ROLLBACK");
        }
        assertThat(clubs.count()).isZero();
        assertThat(managers.count()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class)).isZero();
    }

    private String bootstrapSql() throws Exception {
        String sql = Files.readString(Path.of("scripts/db/bootstrap-initial-data.sql"));
        var variables =
                Map.of(
                        "club_name",
                        "Initial Club",
                        "club_summary",
                        "Summary",
                        "club_description",
                        "Description",
                        "admin_intra_id",
                        "9001",
                        "admin_login",
                        "admin",
                        "admin_name",
                        "Admin",
                        "manager_intra_id",
                        "9002",
                        "manager_login",
                        "chair",
                        "manager_name",
                        "Chair");
        for (var entry : variables.entrySet()) {
            sql = sql.replace(":'" + entry.getKey() + "'", "'" + entry.getValue() + "'");
        }
        return sql;
    }
}
