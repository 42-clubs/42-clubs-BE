package com.clubplatform.user;

import static org.assertj.core.api.Assertions.*;

import java.sql.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class AdminRoleMigrationIntegrationTests {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6");

    @Test
    void mergesExistingRolesWithoutChangingIdentitiesOrRelations() throws Exception {
        var config =
                Flyway.configure()
                        .dataSource(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword());
        config.target("10").load().migrate();
        try (var connection =
                        DriverManager.getConnection(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword());
                var sql = connection.createStatement()) {
            sql.executeUpdate(
                    "INSERT INTO users(id,intra_id,intra_login,display_name,role,created_at,updated_at) VALUES (1,41,'initial','Initial','SUPER_ADMIN','2026-01-01T00:00:00Z',now()),(2,42,'operator','Operator','ADMIN','2026-01-01T00:00:00Z',now()),(3,43,'member','Member','USER','2026-01-01T00:00:00Z',now())");
            sql.executeUpdate(
                    "INSERT INTO clubs(id,name,summary,description,operation_status,recruitment_planned,recruitment_closed_early,created_at,updated_at) VALUES(1,'Club','Summary','Description','ACTIVE',false,false,now(),now())");
            sql.executeUpdate(
                    "INSERT INTO club_managers(user_id,club_id,created_at) VALUES(1,1,now())");
            sql.executeUpdate(
                    "INSERT INTO club_members(user_id,club_id,status,approved_by,joined_at,created_at,updated_at) VALUES(1,1,'ACTIVE',2,now(),now(),now())");
            var flyway =
                    Flyway.configure()
                            .dataSource(
                                    POSTGRES.getJdbcUrl(),
                                    POSTGRES.getUsername(),
                                    POSTGRES.getPassword())
                            .load();
            flyway.migrate();
            flyway.validate();
            try (var rows =
                    sql.executeQuery(
                            "SELECT id,intra_id,intra_login,role,created_at FROM users ORDER BY id")) {
                for (int id = 1; id <= 3; id++) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getLong("id")).isEqualTo(id);
                    assertThat(rows.getLong("intra_id")).isEqualTo(40 + id);
                    assertThat(rows.getString("role")).isEqualTo(id == 3 ? "USER" : "ADMIN");
                    assertThat(rows.getTimestamp("created_at").toInstant())
                            .isEqualTo(java.time.Instant.parse("2026-01-01T00:00:00Z"));
                }
                assertThat(rows.next()).isFalse();
            }
            try (var rows =
                    sql.executeQuery(
                            "SELECT m.user_id,m.club_id,c.approved_by FROM club_managers m JOIN club_members c ON c.user_id=m.user_id AND c.club_id=m.club_id")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong("user_id")).isEqualTo(1);
                assertThat(rows.getLong("club_id")).isEqualTo(1);
                assertThat(rows.getLong("approved_by")).isEqualTo(2);
            }
            assertThatThrownBy(
                            () ->
                                    sql.executeUpdate(
                                            "UPDATE users SET role='SUPER_ADMIN' WHERE id=1"))
                    .isInstanceOf(SQLException.class);
        }
    }
}
