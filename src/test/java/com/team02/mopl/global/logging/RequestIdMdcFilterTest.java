package com.team02.mopl.global.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdMdcFilterTest {

  private final RequestIdMdcFilter requestIdMdcFilter = new RequestIdMdcFilter();

  private MockHttpServletRequest request;
  private MockHttpServletResponse response;

  @BeforeEach
  void setUp() {
    request = new MockHttpServletRequest();
    response = new MockHttpServletResponse();
    MDC.clear();
  }

  @AfterEach
  void tearDown() {
    MDC.clear();
  }

  @Test
  @DisplayName("체인 실행 중에는 MDC에 requestId가 존재한다")
  void success_shouldExposeRequestIdInMdc_whileChainExecutes()
      throws ServletException, IOException {
    // given
    AtomicReference<String> capturedRequestId = new AtomicReference<>();
    FilterChain filterChain =
        (req, res) -> capturedRequestId.set(MDC.get(RequestIdMdcFilter.MDC_KEY));

    // when
    requestIdMdcFilter.doFilterInternal(request, response, filterChain);

    // then - 체인이 실행되던 시점에 MDC 값이 존재했음을 검증
    assertThat(capturedRequestId.get()).isNotNull();
  }

  @Test
  @DisplayName("필터 통과 후에는 MDC에서 requestId가 제거된다")
  void success_shouldClearMdc_afterChainCompletes() throws ServletException, IOException {
    // given
    FilterChain filterChain = (req, res) -> {};

    // when
    requestIdMdcFilter.doFilterInternal(request, response, filterChain);

    // then
    assertThat(MDC.get(RequestIdMdcFilter.MDC_KEY)).isNull();
  }

  @Test
  @DisplayName("체인 실행 중 다른 컴포넌트가 넣은 MDC 값도 요청 종료 후 제거된다")
  void success_shouldClearAllMdcEntries_afterChainCompletes() throws ServletException, IOException {
    // given - 체인 내부에서 다른 키를 MDC에 추가
    FilterChain filterChain = (req, res) -> MDC.put("userId", "someone");

    // when
    requestIdMdcFilter.doFilterInternal(request, response, filterChain);

    // then - requestId 뿐 아니라 다른 키까지 모두 비워짐
    assertThat(MDC.get(RequestIdMdcFilter.MDC_KEY)).isNull();
    assertThat(MDC.get("userId")).isNull();
  }

  @Test
  @DisplayName("응답 헤더 X-Request-Id에 MDC와 동일한 UUID 형식의 requestId가 설정된다")
  void success_shouldSetRequestIdHeader_matchingMdcValue() throws ServletException, IOException {
    // given
    AtomicReference<String> capturedRequestId = new AtomicReference<>();
    FilterChain filterChain =
        (req, res) -> capturedRequestId.set(MDC.get(RequestIdMdcFilter.MDC_KEY));

    // when
    requestIdMdcFilter.doFilterInternal(request, response, filterChain);

    // then
    String headerValue = response.getHeader(RequestIdMdcFilter.HEADER_NAME);
    assertThat(headerValue).isEqualTo(capturedRequestId.get());
    assertDoesNotThrow(() -> UUID.fromString(headerValue));
  }

  @Test
  @DisplayName("체인이 예외를 던져도 MDC에서 requestId가 제거된다")
  void fail_shouldClearMdc_whenChainThrows() {
    // given
    FilterChain filterChain =
        (req, res) -> {
          throw new ServletException("chain failed");
        };

    // when & then
    assertThatThrownBy(() -> requestIdMdcFilter.doFilterInternal(request, response, filterChain))
        .isInstanceOf(ServletException.class);
    assertThat(MDC.get(RequestIdMdcFilter.MDC_KEY)).isNull();
  }
}
