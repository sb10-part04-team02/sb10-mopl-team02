package com.team02.mopl.domain.auth.jwt.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.team02.mopl.global.exception.ErrorResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;

@ExtendWith(MockitoExtension.class)
class JwtLoginFailureHandlerTest {

  @Spy private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
  @InjectMocks private JwtLoginFailureHandler jwtLoginFailureHandler;

  @Test
  @DisplayName("로그인에 실패하면 401을 반환한다")
  void success_shouldReturn401Error_whenAuthenticationFails() throws ServletException, IOException {
    // given
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    AuthenticationException authenticationException = mock(AuthenticationException.class);

    // when
    jwtLoginFailureHandler.onAuthenticationFailure(request, response, authenticationException);

    // then
    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);

    ErrorResponse errorResponse =
        objectMapper.readValue(response.getContentAsString(), ErrorResponse.class);
    assertThat(errorResponse.exceptionName()).isEqualTo("BadCredentialsException");
    assertThat(errorResponse.message()).isEqualTo("인증이 실패했습니다.");
  }

  @Test
  @DisplayName("AuthenticationService쪽 예외면 400을 반환한다")
  void success_shouldReturn400Error_whenAuthenticationServiceExceptionIsProvided()
      throws ServletException, IOException {
    // given
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    AuthenticationException authenticationException = mock(AuthenticationServiceException.class);

    String errorMessage = "invalid-value가 들어왔습니다";
    given(authenticationException.getMessage()).willReturn(errorMessage);

    // when
    jwtLoginFailureHandler.onAuthenticationFailure(request, response, authenticationException);

    // then
    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);

    ErrorResponse errorResponse =
        objectMapper.readValue(response.getContentAsString(), ErrorResponse.class);
    assertThat(errorResponse.exceptionName()).isEqualTo("BadRequestException");
    assertThat(errorResponse.message()).isEqualTo("인증이 실패했습니다.");
    assertThat(errorResponse.details().get("reason")).isEqualTo(errorMessage);
  }
}
