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
import com.team02.mopl.domain.auth.exception.TokenGenerationException;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
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
      throw new TokenGenerationException(e);
    }
  }

  public JWTClaimsSet verifyAccessToken(String token) {
    JWTClaimsSet claimsSet = verifyAndGetClaims(token);

    try {
      String type = claimsSet.getStringClaim("type");
      if (type == null) {
        throw new BadCredentialsException("Token 내에 type이 존재하지 않습니다.");
      }

      if (!TokenType.ACCESS.name().equalsIgnoreCase(type)) {
        throw new BadCredentialsException("Access 토큰이 아닙니다.");
      }

      return claimsSet;

    } catch (ParseException e) {
      throw new BadCredentialsException("Token의 type을 파싱하는데 실패했습니다.", e);
    }
  }

  public JWTClaimsSet verifyRefreshToken(String token) {
    JWTClaimsSet claimsSet = verifyAndGetClaims(token);

    try {
      String type = claimsSet.getStringClaim("type");
      if (type == null) {
        throw new BadCredentialsException("Token 내에 type이 존재하지 않습니다.");
      }

      if (!TokenType.REFRESH.name().equalsIgnoreCase(type)) {
        throw new BadCredentialsException("Refresh 토큰이 아닙니다.");
      }

      return claimsSet;

    } catch (ParseException e) {
      throw new BadCredentialsException("Token의 type을 파싱하는데 실패했습니다.", e);
    }
  }

  private JWTClaimsSet verifyAndGetClaims(String token) {
    try {
      SignedJWT signedJWT = SignedJWT.parse(token);

      // 서명 검증
      if (!signedJWT.verify(verifier)) {
        throw new BadCredentialsException("서명이 올바르지 않은 토큰입니다.");
      }

      // 만료시간 검증
      JWTClaimsSet claimsSet = signedJWT.getJWTClaimsSet();
      Date tokenExpirationTime = claimsSet.getExpirationTime();
      if (tokenExpirationTime == null) {
        throw new InsufficientAuthenticationException("Token 내에 만료시간이 누락되었습니다.");
      }
      if (tokenExpirationTime.before(new Date())) {
        throw new CredentialsExpiredException("만료된 토큰입니다.");
      }

      return claimsSet;

    } catch (ParseException | JOSEException e) {
      throw new BadCredentialsException("올바르지 않은 토큰입니다.");
    }
  }

  enum TokenType {
    ACCESS,
    REFRESH
  }
}
