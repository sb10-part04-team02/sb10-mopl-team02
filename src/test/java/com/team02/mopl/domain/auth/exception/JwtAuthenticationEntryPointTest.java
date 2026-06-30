package com.team02.mopl.domain.auth.exception;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.security.core.AuthenticationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationEntryPointTest {

  @Spy private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
  @InjectMocks private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

  @Test
  @DisplayName("ObjectMapper가 null이면 새로운 ObjectMapper를 생성 후 할당한다")
  void success_shouldAssignNewObjectMapper_whenObjectMapperIsNull() {
    // when
    JwtAuthenticationEntryPoint entryPoint = new JwtAuthenticationEntryPoint(null);

    // then
    ObjectMapper mapper = (ObjectMapper) ReflectionTestUtils.getField(entryPoint, "objectMapper");
    assertThat(mapper).isNotNull();
  }

  @Test
  @DisplayName("인증에 실패하면 401을 반환한다")
  void success_shouldReturn401Error_whenAuthenticationFails() throws ServletException, IOException {
    // given
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    AuthenticationException authenticationException = mock(AuthenticationException.class);

    // when
    jwtAuthenticationEntryPoint.commence(request, response, authenticationException);

    // then
    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);

    ErrorResponse errorResponse =
        objectMapper.readValue(response.getContentAsString(), ErrorResponse.class);
    assertThat(errorResponse.exceptionName()).isEqualTo("AuthenticationException");
    assertThat(errorResponse.message()).isEqualTo("인증이 실패했습니다.");
  }
}
