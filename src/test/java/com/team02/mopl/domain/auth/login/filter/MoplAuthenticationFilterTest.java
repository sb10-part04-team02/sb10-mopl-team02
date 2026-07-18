package com.team02.mopl.domain.auth.login.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.team02.mopl.domain.auth.login.token.MoplAuthenticationToken;
import jakarta.servlet.ServletException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class MoplAuthenticationFilterTest {

  @Mock private AuthenticationManager authenticationManager;
  private MoplAuthenticationFilter authenticationFilter;

  private MockHttpServletRequest request;
  private MockHttpServletResponse response;

  @BeforeEach
  void setUp() {
    request = new MockHttpServletRequest();
    response = new MockHttpServletResponse();

    Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    authenticationFilter = new MoplAuthenticationFilter(validator);
    authenticationFilter.setAuthenticationManager(authenticationManager);
  }

  @Test
  @DisplayName("로그인 인증시도를 하기위한 Authentication을 반환한다")
  void success_shouldReturnAuthentication_whenSignInRequestIsProvided()
      throws ServletException, IOException {
    // given
    String email = "example@gmail.com";
    String password = "password";
    request.setParameter("username", email);
    request.setParameter("password", password);

    Authentication expected = new MoplAuthenticationToken(email, password);
    given(authenticationManager.authenticate(any(Authentication.class))).willReturn(expected);

    // when
    Authentication result = authenticationFilter.attemptAuthentication(request, response);

    // then
    assertThat(result).isEqualTo(expected);
    then(authenticationManager).should().authenticate(any(MoplAuthenticationToken.class));
  }

  @Test
  @DisplayName("파라미터 검증에 실패하면 예외를 던진다")
  void fail_shouldThrowException_whenParameterIsInvalid() {
    // given
    String invalidEmail = "invalid-email";
    String password = "password";
    request.setParameter("username", invalidEmail);
    request.setParameter("password", password);

    // when
    assertThrows(
        BadCredentialsException.class,
        () -> authenticationFilter.attemptAuthentication(request, response));

    // then
    then(authenticationManager).shouldHaveNoInteractions();
  }
}
