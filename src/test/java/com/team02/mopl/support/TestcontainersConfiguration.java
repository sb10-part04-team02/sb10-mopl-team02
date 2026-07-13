package com.team02.mopl.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * 테스트용 PostgreSQL Testcontainer 설정.
 *
 * <p>컨테이너를 빈으로 등록하고 {@link ServiceConnection}으로 DataSource를 자동 연결한다. 스키마는 {@code withInitScript}로
 * 컨테이너 기동 시 1회만 실행된다(Spring sql.init처럼 컨텍스트마다 재실행되지 않음).
 *
 * <p>주의: {@code 01_schema_v9.sql}은 {@code src/main/resources}의 사본이다. 스키마 변경 시 양쪽을 동기화해야 한다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

  @Bean
  @ServiceConnection
  PostgreSQLContainer<?> postgresContainer() {
    return new PostgreSQLContainer<>("postgres:16").withInitScript("01_schema_v9.sql");
  }
}
