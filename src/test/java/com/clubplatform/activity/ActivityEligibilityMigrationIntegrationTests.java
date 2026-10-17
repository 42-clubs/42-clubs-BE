package com.clubplatform.activity;

import static org.assertj.core.api.Assertions.*;

import java.sql.*;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class ActivityEligibilityMigrationIntegrationTests {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6");

    @Test
    void emptyActivitiesUpgradeAddsRequiredPolicyWithoutAnImplicitDefault() throws Exception {
        String schema = "empty_activities";
        migrate(schema, "12");
        var latest = migrate(schema, null);
        latest.validate();
        try (var connection = connection(schema);
                var sql = connection.createStatement();
                var rows =
                        sql.executeQuery(
                                "SELECT is_nullable,column_default FROM information_schema.columns WHERE table_schema='empty_activities' AND table_name='activities' AND column_name='allow_external_participants'")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString("is_nullable")).isEqualTo("NO");
            assertThat(rows.getString("column_default")).isNull();
        }
    }

    @Test
    void unexpectedLegacyActivitiesArePreservedWithoutGuessingTheirPolicy() throws Exception {
        String schema = "legacy_activities";
        var prior = migrate(schema, "12");
        try (var connection = connection(schema);
                var sql = connection.createStatement()) {
            sql.executeUpdate(
                    "INSERT INTO users(id,intra_id,intra_login,display_name,role,created_at,updated_at) VALUES(1,42,'chair','Chair','USER',now(),now())");
            sql.executeUpdate(
                    "INSERT INTO clubs(id,name,summary,description,operation_status,recruitment_planned,recruitment_closed_early,created_at,updated_at) VALUES(1,'Club','Summary','Body','ACTIVE',false,false,now(),now())");
            sql.executeUpdate(
                    "INSERT INTO activities(id,club_id,author_id,organizer_id,title,content,starts_at,recruitment_closes_at,capacity,created_at,updated_at) VALUES(1,1,1,1,'Existing activity','Body',now()+interval '2 hours',now()+interval '1 hour',2,now(),now())");
            assertThatThrownBy(() -> migrate(schema, null)).isInstanceOf(FlywayException.class);
            assertThat(prior.info().current().getVersion().getVersion()).isEqualTo("12");
            try (var rows = sql.executeQuery("SELECT title FROM activities WHERE id=1")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("title")).isEqualTo("Existing activity");
            }
            try (var rows =
                    sql.executeQuery(
                            "SELECT count(*) FROM information_schema.columns WHERE table_schema='legacy_activities' AND table_name='activities' AND column_name='allow_external_participants'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isZero();
            }
        }
    }

    private Flyway migrate(String schema, String target) {
        var config =
                Flyway.configure()
                        .dataSource(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword())
                        .schemas(schema);
        if (target != null) config.target(target);
        var flyway = config.load();
        flyway.migrate();
        return flyway;
    }

    private Connection connection(String schema) throws SQLException {
        var connection =
                DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        connection.setSchema(schema);
        return connection;
    }
}
