package com.team02.mopl.domain.auth.controller;

import com.team02.mopl.domain.auth.dto.JwtDto;
import com.team02.mopl.domain.auth.dto.ResetPasswordRequest;
import com.team02.mopl.domain.auth.dto.SignInRequest;
import com.team02.mopl.domain.auth.exception.AuthException;
import com.team02.mopl.domain.auth.jwt.utils.JwtUtils;
import com.team02.mopl.domain.auth.service.AuthService;
import com.team02.mopl.domain.auth.service.AuthService.TokenResult;
import com.team02.mopl.domain.auth.service.MailService;
import com.team02.mopl.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController implements AuthApi {

  private final AuthService authService;
  private final MailService mailService;
  private final JwtUtils jwtUtils;

  @GetMapping("/csrf-token")
  public ResponseEntity<Void> getCsrfToken(CsrfToken csrfToken) {
    return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
  }

  @PostMapping(value = "/sign-in")
  public void signIn(@Valid SignInRequest request) {
    // SpringSecurity 필터가 요청을 가로채서 로그인 처리를 하므로 실제 실행이 되지 않습니다
    // Swagger 노출용입니다
    throw new AuthException(ErrorCode.INTERNAL_SERVER_ERROR);
  }

  @PostMapping("/sign-out")
  public void signOut() {
    // SpringSecurity 필터가 요청을 가로채서 로그인 처리를 하므로 실제 실행이 되지 않습니다
    // Swagger 노출용입니다
    throw new AuthException(ErrorCode.INTERNAL_SERVER_ERROR);
  }

  @PostMapping("/refresh")
  public ResponseEntity<JwtDto> refresh(
      @CookieValue(name = JwtUtils.REFRESH_TOKEN_COOKIE_NAME) String refreshToken,
      HttpServletResponse response) {
    TokenResult tokens = authService.update(refreshToken);

    ResponseCookie cookie = jwtUtils.generateRefreshTokenCookie(tokens.refreshToken());
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

    return ResponseEntity.status(HttpStatus.OK).body(tokens.jwtDto());
  }

  @PostMapping("/reset-password")
  public ResponseEntity<Void> resetPassword(@RequestBody @Valid ResetPasswordRequest request) {
    mailService.sendResetPasswordEmail(request);
    return ResponseEntity.status(HttpStatus.OK).build();
  }
}
