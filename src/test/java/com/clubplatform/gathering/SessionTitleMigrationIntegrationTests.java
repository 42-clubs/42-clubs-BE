package com.clubplatform.gathering;

import static org.assertj.core.api.Assertions.*;

import java.sql.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class SessionTitleMigrationIntegrationTests {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6");

    @Test
    void upgradingExistingSessionsBackfillsTitleAndPreservesParticipation() throws Exception {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .target("9")
                .load()
                .migrate();
        try (var connection =
                        DriverManager.getConnection(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword());
                var sql = connection.createStatement()) {
            sql.executeUpdate(
                    "INSERT INTO users(id,intra_id,intra_login,display_name,role,created_at,updated_at) VALUES (1,42,'example','Example','USER',now(),now())");
            sql.executeUpdate(
                    "INSERT INTO gatherings(id,organizer_id,title,content,created_at,updated_at) VALUES (1,1,'Reading group','Body',now(),now())");
            sql.executeUpdate(
                    "INSERT INTO gathering_sessions(id,gathering_id,starts_at,ends_at,recruitment_opens_at,recruitment_closes_at,location,capacity,created_at,updated_at) VALUES (1,1,now()+interval '3 hours',now()+interval '4 hours',now(),now()+interval '1 hour','Campus',3,now(),now())");
            sql.executeUpdate(
                    "INSERT INTO gathering_session_participants(session_id,user_id,status,attendance_status,created_at,updated_at) VALUES (1,1,'CONFIRMED','UNCHECKED',now(),now())");
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
                            "SELECT s.title,p.status FROM gathering_sessions s JOIN gathering_session_participants p ON p.session_id=s.id WHERE s.id=1")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("title")).isEqualTo("Reading group");
                assertThat(rows.getString("status")).isEqualTo("CONFIRMED");
            }
            assertThatThrownBy(
                            () ->
                                    sql.executeUpdate(
                                            "UPDATE gathering_sessions SET title=NULL WHERE id=1"))
                    .isInstanceOf(SQLException.class);
        }
    }
}
