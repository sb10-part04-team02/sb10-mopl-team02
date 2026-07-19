package com.team02.mopl.domain.auth.login.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.team02.mopl.domain.auth.login.token.MoplAuthenticationToken;
import jakarta.servlet.ServletException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.io.IOException;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
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
  @DisplayName("이메일 검증에 실패하면 예외를 던진다")
  void fail_shouldThrowException_whenEmailIsInvalid() {
    // given
    String invalidEmail = "invalid-email";
    String password = "password";
    request.setParameter("username", invalidEmail);
    request.setParameter("password", password);

    // when
    assertThrows(
        AuthenticationServiceException.class,
        () -> authenticationFilter.attemptAuthentication(request, response));

    // then
    then(authenticationManager).shouldHaveNoInteractions();
  }

  private static Stream<Arguments> providePasswordBoundaryCases() {
    return Stream.of(
        Arguments.of("", true), // 공백 (실패)
        Arguments.of("1234567", true), // 길이 7 (실패)
        Arguments.of("12345678", false), // 길이 8 (통과)
        Arguments.of("12345678901234567890", false), // 길이 20 (통과)
        Arguments.of("123456789012345678901", true) // 길이 21 (실패)
        );
  }

  @ParameterizedTest
  @MethodSource("providePasswordBoundaryCases")
  @DisplayName("비밀번호의 경계 조건에 따라 검증 예외가 올바르게 발생하는지 확인한다")
  void success_shouldValidatePasswordBoundaries_whenPasswordIsProvided(
      String password, boolean shouldThrowException) {
    // given
    String mail = "example@gmail.com";
    request.setParameter("username", mail);
    request.setParameter("password", password);

    // when & then
    if (shouldThrowException) {
      assertThrows(
          AuthenticationServiceException.class,
          () -> authenticationFilter.attemptAuthentication(request, response));
    } else {
      assertDoesNotThrow(() -> authenticationFilter.attemptAuthentication(request, response));
    }
  }
}
