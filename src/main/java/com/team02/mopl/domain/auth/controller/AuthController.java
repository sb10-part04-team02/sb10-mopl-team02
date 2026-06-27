package com.team02.mopl.domain.auth.controller;

import com.team02.mopl.domain.auth.dto.SignInRequest;
import com.team02.mopl.domain.user.dto.UserDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController implements AuthApi {

  @GetMapping("/csrf-token")
  public ResponseEntity<Void> getCsrfToken(CsrfToken csrfToken) {
    return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
  }

  @PostMapping("/sign-in")
  public ResponseEntity<UserDto> signIn(@RequestBody @Valid SignInRequest request) {
    // SpringSecurity 필터가 요청을 가로채서 로그인 처리를 하므로 실제 실행이 되지 않습니다
    // Swagger 노출용입니다
    throw new IllegalStateException("SpringSecurity필터가 가로채지 못했습니다.");
  }
}
