package com.team02.mopl.domain.auth.service;

import com.nimbusds.jwt.JWTClaimsSet;
import com.team02.mopl.domain.auth.dto.JwtDto;
import com.team02.mopl.domain.auth.entity.MoplUserDetails;
import com.team02.mopl.domain.auth.exception.CompromisedTokenException;
import com.team02.mopl.domain.auth.exception.InvalidTokenException;
import com.team02.mopl.domain.auth.jwt.JwtRegistry;
import com.team02.mopl.domain.auth.jwt.JwtTokenProvider;
import com.team02.mopl.domain.auth.jwt.utils.JwtUtils;
import com.team02.mopl.domain.user.exception.UserNotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

  private final UserDetailsService userDetailsService;
  private final JwtTokenProvider jwtTokenProvider;
  private final JwtRegistry jwtRegistry;
  private final JwtUtils jwtUtils;

  public TokenResult update(String refreshToken) {
    log.debug("토큰 교체 시작");
    JWTClaimsSet verifiedClaimSet;
    UUID userId;

    // 토큰 검증
    try {
      verifiedClaimSet = jwtTokenProvider.verifyRefreshToken(refreshToken);
      userId = jwtUtils.getUserId(verifiedClaimSet);
    } catch (AuthenticationException e) {
      // 만료 or 변조 토큰이어도 지우려는 시도 진행
      try {
        JWTClaimsSet unverifiedClaimSet =
            jwtTokenProvider.parseClaimsWithoutVerification(refreshToken);
        userId = jwtUtils.getUserId(unverifiedClaimSet);

        // 유효하지 않은 토큰이어도 지우기 시도
        jwtRegistry.deleteRefreshToken(userId, refreshToken);
      } catch (Exception ex) {
        // 파싱 실패한 토큰
        log.warn("변조 or 파싱 불가능한 토큰", ex);
      }
      throw new InvalidTokenException();
    }

    // 계정 확인
    String email = verifiedClaimSet.getSubject();
    MoplUserDetails userDetails;
    try {
      userDetails = (MoplUserDetails) userDetailsService.loadUserByUsername(email);
    } catch (UsernameNotFoundException e) {
      // 탈퇴한 유저일 경우
      jwtRegistry.deleteRefreshToken(userId, refreshToken);
      throw new UserNotFoundException();
    }

    // 탈취된 토큰 검증
    if (!jwtRegistry.hasRefreshToken(userId, refreshToken)) {
      jwtRegistry.deleteAllRefreshTokens(userId);
      log.warn("토큰 탈취 의심! 모든 토큰을 파기함: userId={}", userId);
      throw new CompromisedTokenException();
    }

    String newAccessToken = jwtTokenProvider.generateAccessToken(userDetails);
    String newRefreshToken = jwtTokenProvider.generateRefreshToken(userDetails);

    // 토큰 교체
    jwtRegistry.deleteRefreshToken(userId, refreshToken);
    jwtRegistry.registerRefreshToken(userId, newRefreshToken);

    log.info("토큰 교체 완료: userId={}", userId);
    JwtDto jwtDto = new JwtDto(userDetails.getUserDto(), newAccessToken);
    return new TokenResult(jwtDto, newRefreshToken);
  }

  public record TokenResult(JwtDto jwtDto, String refreshToken) {}
}
