package com.team02.mopl.domain.auth.oauth.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

import com.team02.mopl.domain.auth.entity.MoplUserDetails;
import com.team02.mopl.domain.auth.jwt.JwtRegistry;
import com.team02.mopl.domain.auth.jwt.JwtTokenProvider;
import com.team02.mopl.domain.auth.jwt.utils.JwtUtils;
import com.team02.mopl.domain.user.dto.UserDto;
import com.team02.mopl.domain.user.entity.User;
import com.team02.mopl.domain.user.mapper.UserMapper;
import com.team02.mopl.domain.user.repository.UserRepository;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

@ExtendWith(MockitoExtension.class)
class OAuthLoginSuccessHandlerTest {

  @Mock private UserMapper userMapper;
  @Mock private UserRepository userRepository;
  @Mock private JwtTokenProvider jwtTokenProvider;
  @Mock private JwtRegistry jwtRegistry;
  @Mock private JwtUtils jwtUtils;
  @InjectMocks private OAuthLoginSuccessHandler successHandler;

  private MockHttpServletRequest request;
  private MockHttpServletResponse response;
  private Authentication mockAuth;

  @BeforeEach
  void setUp() {
    request = new MockHttpServletRequest();
    response = new MockHttpServletResponse();
    mockAuth = mock(OAuth2AuthenticationToken.class);

    request.setScheme("https");
    request.setServerName("localhost");
    request.setServerPort(8080);
    request.setRequestURI("/login/oauth2/code/*"); // *는 registrationId
  }

  @Test
  @DisplayName("인증객체타입이 맞지않으면 에러문구가 담긴 url을 redirect 한다")
  void fail_shouldRedirectUrl_whenAuthenticationPrincipalTypeIsInvalid() throws IOException {
    // given
    given(mockAuth.getPrincipal()).willReturn(null);

    // when
    successHandler.onAuthenticationSuccess(request, response, mockAuth);

    // then
    assertThat(response.getRedirectedUrl())
        .startsWith("https://localhost:8080/#/sign-in?error=oauth_failed")
        .contains(
            "error_message=" + URLEncoder.encode("인증 객체 타입이 맞지 않습니다.", StandardCharsets.UTF_8));
  }

  @Test
  @DisplayName("유저를 찾을 수 없으면 에러문구가 담긴 url을 redirect 한다")
  void fail_shouldRedirectUrl_whenUserNotFound() throws IOException {
    // given
    OidcUser mockOidcUser = mock(OidcUser.class);
    given(mockAuth.getPrincipal()).willReturn(mockOidcUser);
    given(mockOidcUser.getAttribute(anyString())).willReturn("socialUserId");

    String registrationId = "google";
    given(((OAuth2AuthenticationToken) mockAuth).getAuthorizedClientRegistrationId())
        .willReturn(registrationId);
    given(userRepository.findBySubjectAndProviderAndDeletedAtIsNull(any(), anyString()))
        .willReturn(Optional.empty());

    // when
    successHandler.onAuthenticationSuccess(request, response, mockAuth);

    // then
    assertThat(response.getRedirectedUrl())
        .startsWith("https://localhost:8080/#/sign-in?error=oauth_failed")
        .contains("error_message=" + URLEncoder.encode("유저를 찾을 수 없습니다.", StandardCharsets.UTF_8));
  }

  @Test
  @DisplayName("토큰을 발급하고 response에 담아 메인페이지 url에 redirect 한다")
  void success_shouldIssueTokensAndRedirectToMainPage_whenAuthenticationSucceeds()
      throws IOException {
    // given
    OidcUser mockOidcUser = mock(OidcUser.class);
    given(mockAuth.getPrincipal()).willReturn(mockOidcUser);
    given(mockOidcUser.getAttribute(anyString())).willReturn("socialUserId");

    User mockUser = mock(User.class);
    String registrationId = "google";
    given(((OAuth2AuthenticationToken) mockAuth).getAuthorizedClientRegistrationId())
        .willReturn(registrationId);
    given(userRepository.findBySubjectAndProviderAndDeletedAtIsNull(any(), anyString()))
        .willReturn(Optional.of(mockUser));

    UserDto mockUserDto = mock(UserDto.class);
    given(userMapper.toDto(mockUser)).willReturn(mockUserDto);

    String refreshToken = "refreshToken";
    given(jwtTokenProvider.generateRefreshToken(any(MoplUserDetails.class)))
        .willReturn(refreshToken);

    ResponseCookie mockCookie =
        ResponseCookie.from("REFRESH_TOKEN", refreshToken).path("/").httpOnly(true).build();
    given(jwtUtils.generateRefreshTokenCookie(refreshToken)).willReturn(mockCookie);

    // when
    successHandler.onAuthenticationSuccess(request, response, mockAuth);

    // then
    then(jwtRegistry).should(times(1)).registerRefreshToken(any(), eq(refreshToken));
    assertThat(response.getRedirectedUrl()).isEqualTo("https://localhost:8080/");
    assertThat(response.getCookies())
        .anyMatch(
            cookie ->
                "REFRESH_TOKEN".equals(cookie.getName()) && refreshToken.equals(cookie.getValue()));
  }
}
