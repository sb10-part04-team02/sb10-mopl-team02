package com.team02.mopl.domain.auth.oauth.handler;

import static com.team02.mopl.domain.auth.oauth.handler.OAuthLoginFailureHandler.generateErrorUrl;

import com.team02.mopl.domain.auth.entity.MoplUserDetails;
import com.team02.mopl.domain.auth.jwt.JwtRegistry;
import com.team02.mopl.domain.auth.jwt.JwtTokenProvider;
import com.team02.mopl.domain.auth.jwt.utils.JwtUtils;
import com.team02.mopl.domain.auth.oauth.provider.OAuthType;
import com.team02.mopl.domain.user.entity.User;
import com.team02.mopl.domain.user.mapper.UserMapper;
import com.team02.mopl.domain.user.repository.UserRepository;
import com.team02.mopl.global.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Component
@RequiredArgsConstructor
public class OAuthLoginSuccessHandler implements AuthenticationSuccessHandler {

  private final UserMapper userMapper;
  private final UserRepository userRepository;
  private final JwtTokenProvider jwtTokenProvider;
  private final JwtRegistry jwtRegistry;
  private final JwtUtils jwtUtils;

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws IOException {

    // baseUrl 추출
    String baseUrl = extractBaseUrl(request);

    // oidcUser 타입이 아닌경우
    if (!(authentication.getPrincipal() instanceof OidcUser oidcUser)) {
      String errorUrl = generateErrorUrl(baseUrl, "인증 객체 타입이 맞지 않습니다.");
      response.sendRedirect(errorUrl);
      return;
    }

    String registrationId =
        ((OAuth2AuthenticationToken) authentication).getAuthorizedClientRegistrationId();
    OAuthType provider = OAuthType.of(registrationId);
    Optional<User> optionalUser =
        userRepository.findBySubjectAndProviderAndDeletedAtIsNull(
            provider, oidcUser.getAttribute("sub"));
    // 유저를 찾을 수 없는 경우
    if (optionalUser.isEmpty()) {
      String errorUrl = generateErrorUrl(baseUrl, "유저를 찾을 수 없습니다.");
      response.sendRedirect(errorUrl);
      return;
    }

    User findUser = optionalUser.get();
    // 계정이 잠긴 경우
    if (findUser.isLocked()) {
      response.sendRedirect(generateErrorUrl(baseUrl, "잠긴 계정입니다."));
      return;
    }

    // generateRefreshToken활용을 위한 생성
    MoplUserDetails userDetails = new MoplUserDetails(userMapper.toDto(findUser), null);

    // 토큰발급
    String refreshToken = jwtTokenProvider.generateRefreshToken(userDetails);
    String accessToken = jwtTokenProvider.generateAccessToken(userDetails);

    try {
      // 토큰 Redis 등록
      jwtRegistry.registerToken(findUser.getId(), refreshToken, accessToken);
    } catch (BusinessException e) {
      response.sendRedirect(generateErrorUrl(baseUrl, "서버 내부 오류가 발생했습니다."));
      return;
    } catch (BadCredentialsException e) {
      response.sendRedirect(generateErrorUrl(baseUrl, "유효한 토큰이 아닙니다."));
      return;
    }

    // refresh 토큰 헤더에 등록
    ResponseCookie cookie = jwtUtils.generateRefreshTokenCookie(refreshToken);
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

    response.sendRedirect(baseUrl);
  }

  public static String extractBaseUrl(HttpServletRequest request) {
    return ServletUriComponentsBuilder.fromContextPath(request).path("/").build().toUriString();
  }
}
