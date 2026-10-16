package com.clubplatform.notice;

import static org.assertj.core.api.Assertions.*;

import java.sql.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class NoticeMigrationIntegrationTests {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6");

    @Test
    void upgradingExistingNoticesDefaultsToPrivateAndPreservesContentAndForeignKeys()
            throws Exception {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .target("7")
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
                    "INSERT INTO clubs(id,name,summary,description,operation_status,created_at,updated_at) VALUES (1,'Club','Summary','Description','ACTIVE',now(),now())");
            sql.executeUpdate(
                    "INSERT INTO notices(id,club_id,author_id,title,content,created_at,updated_at) VALUES (1,1,1,'Existing','Body',now(),now())");
        }
        var flyway =
                Flyway.configure()
                        .dataSource(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword())
                        .load();
        flyway.migrate();
        flyway.validate();
        try (var connection =
                        DriverManager.getConnection(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword());
                var sql = connection.createStatement()) {
            try (var rows =
                    sql.executeQuery(
                            "SELECT club_id,author_id,title,content,visibility FROM notices WHERE id=1")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong("club_id")).isEqualTo(1);
                assertThat(rows.getLong("author_id")).isEqualTo(1);
                assertThat(rows.getString("title")).isEqualTo("Existing");
                assertThat(rows.getString("content")).isEqualTo("Body");
                assertThat(rows.getString("visibility")).isEqualTo("MEMBERS_ONLY");
            }
            assertThatThrownBy(
                            () ->
                                    sql.executeUpdate(
                                            "UPDATE notices SET visibility='INVALID' WHERE id=1"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(
                            () ->
                                    sql.executeUpdate(
                                            "UPDATE notices SET visibility=NULL WHERE id=1"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> sql.executeUpdate("UPDATE notices SET club_id=999 WHERE id=1"))
                    .isInstanceOf(SQLException.class);
            sql.executeUpdate(
                    "INSERT INTO notices(id,club_id,author_id,title,content,created_at,updated_at) VALUES (2,1,1,'Default','Body',now(),now())");
            try (var rows = sql.executeQuery("SELECT visibility FROM notices WHERE id=2")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("MEMBERS_ONLY");
            }
            assertThatThrownBy(
                            () ->
                                    sql.executeUpdate(
                                            "INSERT INTO announcements(author_id,title,content,created_at,updated_at) VALUES (999,'Invalid','Body',now(),now())"))
                    .isInstanceOf(SQLException.class);
        }
    }
}
