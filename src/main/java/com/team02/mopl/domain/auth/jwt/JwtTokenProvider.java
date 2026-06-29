package com.team02.mopl.domain.auth.jwt;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTClaimsSet.Builder;
import com.nimbusds.jwt.SignedJWT;
import com.team02.mopl.domain.auth.entity.MoplUserDetails;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

@Slf4j
@EnableConfigurationProperties(JwtProperties.class)
@RequiredArgsConstructor
@Component
public class JwtTokenProvider {

  private final JwtProperties properties;

  private JWSSigner signer;
  private JWSVerifier verifier;

  @PostConstruct
  public void bakeSignerAndVerifier() throws JOSEException {
    byte[] keyBytes = properties.secretKey().getBytes(StandardCharsets.UTF_8);
    this.signer = new MACSigner(keyBytes);
    this.verifier = new MACVerifier(keyBytes);
  }

  public String generateAccessToken(MoplUserDetails userDetails) {
    return generateToken(userDetails, TokenType.ACCESS);
  }

  public String generateRefreshToken(MoplUserDetails userDetails) {
    return generateToken(userDetails, TokenType.REFRESH);
  }

  // TODO:  Exception을 어떻게 처리해야할지 나중에 구현(임시)
  private String generateToken(MoplUserDetails userDetails, TokenType type) {
    long expirationTime =
        switch (type) {
          case ACCESS -> properties.accessTokenExpiration().toMillis();
          case REFRESH -> properties.refreshTokenExpiration().toMillis();
        };

    Date expDate = new Date(System.currentTimeMillis() + expirationTime);
    List<String> roles =
        userDetails.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    JWTClaimsSet claimsSet =
        new Builder()
            .jwtID(UUID.randomUUID().toString())
            .subject(userDetails.getUserDto().email())
            .issueTime(new Date())
            .expirationTime(expDate)
            .claim("roles", roles)
            .claim("type", type.name().toLowerCase())
            .claim("userId", userDetails.getUserDto().id().toString())
            .build();
    SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claimsSet);

    try {
      signedJWT.sign(signer);
      return signedJWT.serialize();
    } catch (JOSEException e) {
      log.error("Token을 생성하는데 실패했습니다.");
      throw new RuntimeException("Token을 생성하는데 실패했습니다.", e);
    }
  }

  // TODO:  Exception을 어떻게 처리해야할지 나중에 구현(임시)
  public JWTClaimsSet verifyAccessToken(String token) throws ParseException {
    JWTClaimsSet claimsSet = verifyAndGetClaims(token);
    if (!TokenType.ACCESS.name().equalsIgnoreCase(claimsSet.getStringClaim("type"))) {
      throw new RuntimeException("유효하지 않은 토큰입니다.");
    }

    return claimsSet;
  }

  // TODO:  Exception을 어떻게 처리해야할지 나중에 구현(임시)
  public JWTClaimsSet verifyRefreshToken(String token) throws ParseException {
    JWTClaimsSet claimsSet = verifyAndGetClaims(token);
    if (!TokenType.REFRESH.name().equalsIgnoreCase(claimsSet.getStringClaim("type"))) {
      throw new RuntimeException("유효하지 않은 토큰입니다.");
    }

    return claimsSet;
  }

  // TODO:  Exception을 어떻게 처리해야할지 나중에 구현(임시)
  private JWTClaimsSet verifyAndGetClaims(String token) {
    try {
      SignedJWT signedJWT = SignedJWT.parse(token);

      // 서명 검증
      if (!signedJWT.verify(verifier)) {
        log.debug("JWT 검증 실패: 서명");
        throw new RuntimeException("서명이 올바르지 않은 토큰입니다.");
      }

      // 만료시간 검증
      JWTClaimsSet claimsSet = signedJWT.getJWTClaimsSet();
      Date tokenExpirationTime = claimsSet.getExpirationTime();
      if (tokenExpirationTime == null || tokenExpirationTime.before(new Date())) {
        log.debug("JWT 검증 실패: 만료시간이 없거나 만료됨");
        throw new RuntimeException("만료되었거나 올바르지 않은 토큰입니다.");
      }

      return claimsSet;

    } catch (ParseException e) {
      log.debug("Token을 파싱하는데 실패했습니다.");
      throw new RuntimeException("Token을 파싱하는데 실패했습니다.", e);
    } catch (JOSEException e) {
      log.debug("Token을 검증하는데 실패했습니다.");
      throw new RuntimeException("Token을 검증하는데 실패했습니다.", e);
    }
  }

  enum TokenType {
    ACCESS,
    REFRESH
  }
}
