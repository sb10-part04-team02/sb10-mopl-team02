package com.team02.mopl.global.config;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.oneOf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team02.mopl.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Actuator 엔드포인트 접근 제어 검증.
 *
 * <p>모니터링용으로 열어둔 health/info/prometheus는 인증 없이 접근 가능해야 하고(Docker HEALTHCHECK, Prometheus 스크레이프), 그
 * 외 actuator 엔드포인트는 인증 여부와 무관하게 차단되어야 한다.
 */
@AutoConfigureMockMvc
// 테스트에서는 메트릭 export가 기본 비활성화라 PrometheusMeterRegistry가 생성되지 않으므로 명시적으로 켠다
@AutoConfigureObservability(tracing = false)
class ActuatorSecurityTest extends IntegrationTestSupport {

  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName("/actuator/prometheus는 인증 없이 접근 가능하고 Prometheus 포맷 메트릭을 반환한다")
  void prometheusEndpointIsAccessibleWithoutAuth() throws Exception {
    mockMvc
        .perform(get("/actuator/prometheus"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("jvm_memory_used_bytes")));
  }

  @Test
  @DisplayName("/actuator/health는 인증 없이 접근 가능하다 (테스트 환경은 Redis 미기동으로 503일 수 있음)")
  void healthEndpointIsAccessibleWithoutAuth() throws Exception {
    // 401/403이 아니면 인증 차단 없이 헬스 엔드포인트에 도달한 것
    mockMvc.perform(get("/actuator/health")).andExpect(status().is(oneOf(200, 503)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"/actuator/env", "/actuator/beans", "/actuator/metrics"})
  @DisplayName("허용 목록 외 actuator 엔드포인트는 익명 접근 시 401을 반환한다")
  void otherActuatorEndpointsAreBlockedForAnonymous(String path) throws Exception {
    mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
  }

  @ParameterizedTest
  @ValueSource(strings = {"/actuator/env", "/actuator/beans", "/actuator/metrics"})
  @WithMockUser
  @DisplayName("허용 목록 외 actuator 엔드포인트는 인증된 사용자도 403으로 차단된다")
  void otherActuatorEndpointsAreBlockedForAuthenticatedUser(String path) throws Exception {
    mockMvc.perform(get(path)).andExpect(status().isForbidden());
  }
}
