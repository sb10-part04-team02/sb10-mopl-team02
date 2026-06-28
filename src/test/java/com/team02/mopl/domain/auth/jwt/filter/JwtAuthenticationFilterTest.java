package com.team02.mopl.domain.auth.jwt.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

  @Mock private AuthenticationManager authenticationManager;

  @InjectMocks private JwtAuthenticationFilter jwtAuthenticationFilter;

  @BeforeEach
  void setUp() {
    // 테스트간 격리를 위해 비워둠
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("Authorization 헤더가 없으면 인증을 수행하지 않고 다음 필터로 통과한다")
  void fail_shouldNotAuthenticate_whenAuthorizationHeaderIsAbsent()
      throws ServletException, IOException {
    // given
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain filterChain = new MockFilterChain();

    // when
    jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

    // then
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    assertThat(authentication).isNull();
  }

  @Test
  @DisplayName("다른 타입의 인증토큰이 있으면 인증을 수행하지 않고 다음 필터로 통과한다")
  void fail_shouldNotAuthenticate_whenAuthorizationHeaderIsInvalid()
      throws ServletException, IOException {
    // given
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain filterChain = new MockFilterChain();

    String accessToken = "accessToken";
    request.addHeader("Authorization", "Basic " + accessToken);

    // when
    jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

    // then
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    assertThat(authentication).isNull();
  }

  @Test
  @DisplayName("올바른 Bearer 토큰이 있으면 인증객체를 SecurityContextHolder에 저장한다")
  void success_shouldSaveAuthentication_whenTokenIsValid() throws ServletException, IOException {
    // given
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain filterChain = new MockFilterChain();

    String accessToken = "accessToken";
    request.addHeader("Authorization", "Bearer " + accessToken);

    Authentication expectAuthentication = mock(Authentication.class);
    given(authenticationManager.authenticate(any())).willReturn(expectAuthentication);

    // when
    jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

    // then
    Authentication actual = SecurityContextHolder.getContext().getAuthentication();
    assertThat(actual).isEqualTo(expectAuthentication);
  }
}
