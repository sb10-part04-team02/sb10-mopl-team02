package com.team02.mopl.domain.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.team02.mopl.domain.auth.entity.MoplUserDetails;
import com.team02.mopl.domain.auth.jwt.JwtTokenProvider.TokenType;
import com.team02.mopl.domain.user.dto.UserDto;
import com.team02.mopl.domain.user.entity.enums.Role;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class JwtTokenProviderTest {

  private static final String validKey = "12345678901234567890123456789012"; // 32바이트

  @Mock private JwtProperties properties;

  @InjectMocks private JwtTokenProvider jwtTokenProvider;

  @Nested
  class VerifyAndGetClaims {
    @Test
    @DisplayName("parse를 실패해서 ParseException을 던진다")
    void fail_shouldThrowRuntimeExceptionWrappedParseException_whenTokenParsingFails() {
      // given
      String invalidToken = "invalidToken";

      // when
      assertThrows(RuntimeException.class, () -> jwtTokenProvider.verifyAndGetClaims(invalidToken));
    }

    @Test
    @DisplayName("verify를 실패해서 JOSEException을 던진다")
    void
        fail_shouldThrowRuntimeExceptionWrappedJOSEException_whenTokenVerificationFailsWithJOSEException()
            throws JOSEException {
      // given
      String validFormatToken = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0In0.signature";
      JWSVerifier mockVerifier = mock(JWSVerifier.class);
      // JOSE예외는 SignedJWT가 아니라 내부적으로 verifier가 발생시킴
      lenient().doThrow(new JOSEException()).when(mockVerifier).verify(any(), any(), any());
      ReflectionTestUtils.setField(jwtTokenProvider, "verifier", mockVerifier);

      // when & then
      assertThrows(
          RuntimeException.class, () -> jwtTokenProvider.verifyAndGetClaims(validFormatToken));
    }

    @Test
    @DisplayName("서명검증에 실패해서 RuntimeException을 던진다")
    void fail_shouldThrowRuntimeException_whenTokenSignatureIsInvalid() throws JOSEException {
      // given
      String validFormatToken = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0In0.signature";
      JWSVerifier mockVerifier = mock(JWSVerifier.class);
      given(mockVerifier.verify(any(), any(), any())).willReturn(false);
      ReflectionTestUtils.setField(jwtTokenProvider, "verifier", mockVerifier);

      // when & then
      assertThrows(
          RuntimeException.class, () -> jwtTokenProvider.verifyAndGetClaims(validFormatToken));
    }

    @Test
    @DisplayName("만료시간이 null이라 검증에 실패해서 RuntimeException을 던진다")
    void fail_shouldThrowRuntimeException_whenTokenExpirationTimeIsNull() throws JOSEException {
      // given
      given(properties.secretKey()).willReturn(validKey);
      jwtTokenProvider.bakeSignerAndVerifier();

      // 만료시간 없는 claim 생성
      JWTClaimsSet claimsSet = new JWTClaimsSet.Builder().issueTime(new Date()).build();

      // 만료시간 없는 토큰 생성
      SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claimsSet);
      signedJWT.sign((JWSSigner) ReflectionTestUtils.getField(jwtTokenProvider, "signer"));
      String nullExpirationToken = signedJWT.serialize();

      // when & then
      assertThrows(
          RuntimeException.class, () -> jwtTokenProvider.verifyAndGetClaims(nullExpirationToken));
    }

    @Test
    @DisplayName("만료시간 검증에 실패해서 RuntimeException을 던진다")
    void fail_shouldThrowRuntimeException_whenTokenIsExpired() throws JOSEException {
      // given
      given(properties.secretKey()).willReturn(validKey);
      jwtTokenProvider.bakeSignerAndVerifier();

      // 만료시간 지난 claim 생성
      Date pastDate = new Date(System.currentTimeMillis() - Duration.ofMinutes(10).toMillis());
      JWTClaimsSet claimsSet =
          new JWTClaimsSet.Builder().issueTime(new Date()).expirationTime(pastDate).build();

      // 만료시간 지난 토큰 생성
      SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claimsSet);
      signedJWT.sign((JWSSigner) ReflectionTestUtils.getField(jwtTokenProvider, "signer"));
      String expiredToken = signedJWT.serialize();

      // when & then
      assertThrows(RuntimeException.class, () -> jwtTokenProvider.verifyAndGetClaims(expiredToken));
    }

    @Test
    @DisplayName("ClaimSet을 정상적으로 반환한다")
    void success_shouldReturnJWTClaimsSet_whenTokenIsValid() throws JOSEException {
      // given
      given(properties.secretKey()).willReturn(validKey);
      jwtTokenProvider.bakeSignerAndVerifier();

      // 만료시간이 10분인 claim 생성
      Date currentPlus10Minute =
          new Date(System.currentTimeMillis() + Duration.ofMinutes(10).toMillis());
      JWTClaimsSet expect =
          new JWTClaimsSet.Builder()
              .issueTime(new Date())
              .expirationTime(currentPlus10Minute)
              .build();

      // 만료시간 지난 토큰 생성
      SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), expect);
      signedJWT.sign((JWSSigner) ReflectionTestUtils.getField(jwtTokenProvider, "signer"));
      String validToken = signedJWT.serialize();

      // when
      JWTClaimsSet actual = jwtTokenProvider.verifyAndGetClaims(validToken);

      // then
      // JWT토큰은 밀리초단위가 절삭되서 1초내로 비교해야 함
      assertThat(actual.getExpirationTime()).isCloseTo(expect.getExpirationTime(), 1000L);
    }
  }

  @Nested
  class GenerateToken {

    private static final UUID userId = UUID.randomUUID();
    private static final String email = "example@gmail.com";

    @Test
    @DisplayName("토큰 서명중 JOSEException이 발생하면 RuntimeException을 던진다")
    void fail_shouldThrowRuntimeException_whenSigningFailsWithJOSEException() throws JOSEException {
      // given
      UserDto userDto = new UserDto(userId, Instant.now(), email, "이름", null, Role.USER, false);
      MoplUserDetails userDetails = new MoplUserDetails(userDto, "encryptedPassword");
      given(properties.accessTokenExpiration()).willReturn(Duration.ofMinutes(10));
      // JOSE예외는 SignedJWT가 아니라 내부적으로 signer가 발생시킴
      JWSSigner mockSigner = mock(JWSSigner.class);
      lenient().doThrow(new JOSEException()).when(mockSigner).sign(any(), any());
      ReflectionTestUtils.setField(jwtTokenProvider, "signer", mockSigner);

      // when & then
      assertThrows(RuntimeException.class, () -> jwtTokenProvider.generateAccessToken(userDetails));
    }

    @ParameterizedTest
    @EnumSource(TokenType.class)
    @DisplayName("MoplUserDetails를 가지고 Access, Refresh Token을 성공적으로 생성한다")
    void success_shouldGenerateTokenSuccessfully_whenGivenMoplUserDetailsWithTokenType(
        TokenType type) throws JOSEException {
      // given
      UserDto userDto = new UserDto(userId, Instant.now(), email, "이름", null, Role.USER, false);
      MoplUserDetails userDetails = new MoplUserDetails(userDto, "encryptedPassword");

      given(properties.secretKey()).willReturn(validKey);
      jwtTokenProvider.bakeSignerAndVerifier();

      // when
      String token =
          switch (type) {
            case ACCESS -> {
              given(properties.accessTokenExpiration()).willReturn(Duration.ofMinutes(10));
              yield jwtTokenProvider.generateAccessToken(userDetails);
            }
            case REFRESH -> {
              given(properties.refreshTokenExpiration()).willReturn(Duration.ofDays(7));
              yield jwtTokenProvider.generateRefreshToken(userDetails);
            }
          };

      // then
      assertThat(token).isNotEmpty();
      assertDoesNotThrow(
          () -> {
            // 토큰이 생성이 잘 되었는지 검증
            SignedJWT signedJWT = SignedJWT.parse(token);
            JWTClaimsSet claimsSet = signedJWT.getJWTClaimsSet();

            assertThat(claimsSet.getSubject()).isEqualTo(email);
            assertThat(claimsSet.getStringClaim("userId")).isEqualTo(userId.toString());
            assertThat(claimsSet.getStringClaim("type")).isEqualTo(type.name().toLowerCase());
          });
    }
  }

  @Nested
  class BakeSignerAndVerifier {
    @Test
    @DisplayName("키 길이가 짧아 JOSEException을 던진다")
    void fail_shouldThrowJOSEException_whenSecretKeyUnder32Bytes() {
      // given
      given(properties.secretKey()).willReturn("shortKey");

      // when & then
      assertThrows(JOSEException.class, () -> jwtTokenProvider.bakeSignerAndVerifier());
    }

    @Test
    @DisplayName("키 길이가 32Bytes보다 같거나 크면 정상 진행된다")
    void success_shouldInitializeSuccessfully_whenSecretKeyGreaterThanOrEqualTo32Bytes() {
      // given
      given(properties.secretKey()).willReturn(validKey);

      // when & then
      assertDoesNotThrow(() -> jwtTokenProvider.bakeSignerAndVerifier());
    }
  }
}
