package com.clubplatform.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/** 통합 테스트에 독립된 PostgreSQL 컨테이너와 연결 정보를 제공한다. */
@TestConfiguration(proxyBeanMethods = false)
public class PostgreSqlTestConfig {

    /** Spring이 생명주기를 관리할 테스트용 PostgreSQL 컨테이너를 생성한다. */
    @Bean
    @ServiceConnection
    public PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>("postgres:17.6-alpine");
    }
}
