package com.team02.mopl.domain.auth.jwt.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.team02.mopl.domain.auth.entity.MoplUserDetails;
import com.team02.mopl.domain.auth.jwt.JwtRegistry;
import com.team02.mopl.domain.auth.jwt.JwtTokenProvider;
import com.team02.mopl.domain.auth.jwt.utils.JwtUtils;
import com.team02.mopl.domain.user.dto.UserDto;
import com.team02.mopl.domain.user.entity.enums.Role;
import com.team02.mopl.global.exception.ErrorResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class JwtLoginSuccessHandlerTest {

  @Spy private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
  @Mock private JwtTokenProvider jwtTokenProvider;
  @Mock private JwtRegistry jwtRegistry;
  @Mock private JwtUtils jwtUtils;
  @InjectMocks private JwtLoginSuccessHandler jwtLoginSuccessHandler;

  @Test
  @DisplayName("인증객체타입이 맞지않아 401을 반환한다")
  void fail_shouldReturn401Error_whenAuthenticationPrincipalTypeIsInvalid()
      throws ServletException, IOException {
    // given
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    Authentication authentication = mock(Authentication.class);
    given(authentication.getPrincipal()).willReturn("invalid type");

    // when
    jwtLoginSuccessHandler.onAuthenticationSuccess(request, response, authentication);

    // then
    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    String json = response.getContentAsString();
    ErrorResponse errorResponse = objectMapper.readValue(json, ErrorResponse.class);
    assertThat(errorResponse.exceptionName()).isEqualTo("BadCredentialsException");
    assertThat(errorResponse.message()).isEqualTo("인증 객체 타입이 맞지 않습니다.");
  }

  @Test
  @DisplayName("인증 성공시 JwtDto 반환과 쿠키를 설정한다")
  void success_shouldReturnJwtDtoAndSetCookie_whenAuthenticationSucceeds()
      throws ServletException, IOException {
    // given
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    UUID userId = UUID.randomUUID();
    UserDto userDto =
        new UserDto(userId, Instant.now(), "example@gmail.com", "이름", null, Role.USER, false);
    MoplUserDetails userDetails = new MoplUserDetails(userDto, "encryptedPassword");

    String accessToken = "accessToken";
    String refreshToken = "refreshToken";
    given(jwtTokenProvider.generateAccessToken(any())).willReturn(accessToken);
    given(jwtTokenProvider.generateRefreshToken(any())).willReturn(refreshToken);
    Authentication authentication = mock(Authentication.class);
    given(authentication.getPrincipal()).willReturn(userDetails);

    ResponseCookie cookie = ResponseCookie.from(HttpHeaders.SET_COOKIE, refreshToken).build();
    given(jwtUtils.generateRefreshTokenCookie(anyString())).willReturn(cookie);

    // when
    jwtLoginSuccessHandler.onAuthenticationSuccess(request, response, authentication);

    // then
    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).contains(refreshToken);
    assertThat(response.getContentAsString()).contains(accessToken);
    then(jwtRegistry).should(times(1)).registerRefreshToken(userId, refreshToken);
  }
}
