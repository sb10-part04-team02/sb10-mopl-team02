package com.team02.mopl.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * 테스트용 PostgreSQL Testcontainer 설정.
 *
 * <p>컨테이너를 빈으로 등록하고 {@link ServiceConnection}으로 DataSource를 자동 연결한다. 스키마는 Flyway가 {@code
 * src/main/resources/db/migration}의 마이그레이션으로 생성하므로, 여기서 별도로 주입하지 않는다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

  @Bean
  @ServiceConnection
  PostgreSQLContainer<?> postgresContainer() {
    return new PostgreSQLContainer<>("postgres:16");
  }
}
