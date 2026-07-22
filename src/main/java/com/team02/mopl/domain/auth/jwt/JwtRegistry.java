package com.team02.mopl.domain.auth.jwt;

import com.team02.mopl.domain.auth.jwt.utils.JwtUtils;
import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.global.exception.ErrorCode;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtRegistry {

  @Value("${app.jwt.redis.refresh-prefix}")
  private String refreshPrefix;

  @Value("${app.jwt.redis.active-access-prefix}")
  private String activeAccessPrefix;

  @Value("${app.jwt.redis.max-account-count}")
  private long maxAccountCount;

  @Value("${app.jwt.redis.blacklist-prefix}")
  private String blacklistPrefix;

  @Value("${app.jwt.redis.user-lock-prefix}")
  private String userLockPrefix;

  @Value("${app.jwt.redis.temp-password-prefix}")
  private String tempPwPrefix;

  private final JwtProperties properties;
  private final JwtUtils jwtUtils;
  private final StringRedisTemplate redisTemplate;

  private static final RedisScript<Long> RELEASE_SCRIPT =
      new DefaultRedisScript<>(
          "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
          Long.class);
  private static final RedisScript<String> REGISTER_SCRIPT =
      new DefaultRedisScript<>(
          """
          local refreshKey = KEYS[1]
          local accessKey = KEYS[2]

          local refreshToken = ARGV[1]
          local accessToken = ARGV[2]
          local now = tonumber(ARGV[3])
          local refreshExpirationMillis = tonumber(ARGV[4])
          local tokenExpirationTime = tonumber(ARGV[5])
          local maxAccountCount = tonumber(ARGV[6])
          local remainingAccessMillis = tonumber(ARGV[7])

          -- 만료시간 토큰 지우기
          redis.call('ZREMRANGEBYSCORE', refreshKey, 0, now)
          -- 순서있는 Set(만료시간을 기준으로 정렬됨)
          redis.call('ZADD', refreshKey, tokenExpirationTime, refreshToken)

          -- 개수제한
          local currentCount = redis.call('ZCARD', refreshKey)
          if currentCount > maxAccountCount then
              local removeCount = currentCount - maxAccountCount
              redis.call('ZREMRANGEBYRANK', refreshKey, 0, removeCount - 1)
          end

          -- refresh 토큰키값 최신화
          local refreshTtlSeconds = math.ceil(refreshExpirationMillis / 1000)
          redis.call('EXPIRE', refreshKey, refreshTtlSeconds)

          -- access 토큰 저장
          if remainingAccessMillis > 0 then
              redis.call('PSETEX', accessKey, remainingAccessMillis, accessToken)
          end

          return "OK"
          """,
          String.class);

  public void registerToken(UUID userId, String refreshToken, String accessToken) {
    String refreshKey = getRefreshKey(userId);
    String accessKey = getActiveAccessKey(userId, accessToken);

    long now = System.currentTimeMillis();
    long refreshExpirationMillis = properties.refreshTokenExpiration().toMillis();
    long tokenExpirationTime = now + refreshExpirationMillis;
    long remainingAccessMillis = jwtUtils.getRemainingTimeToExpiration(accessToken).toMillis();

    try {
      redisTemplate.execute(
          REGISTER_SCRIPT,
          List.of(refreshKey, accessKey),
          refreshToken,
          accessToken,
          String.valueOf(now),
          String.valueOf(refreshExpirationMillis),
          String.valueOf(tokenExpirationTime),
          String.valueOf(maxAccountCount),
          String.valueOf(remainingAccessMillis));
    } catch (DataAccessException e) {
      log.error("[Redis] 토큰 등록 중 네트워크/redis 장애 발생 - userId: {}", userId, e);
      throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
    }
  }

  private String getRefreshKey(UUID userId) {
    return refreshPrefix + userId.toString();
  }

  private String getActiveAccessKey(UUID userId, String accessToken) {
    String accessTokenId = jwtUtils.getTokenId(accessToken);
    if (accessTokenId == null) {
      throw new BadCredentialsException("토큰 식별자가 없는 유효하지 않은 토큰입니다.");
    }
    return activeAccessPrefix + userId.toString() + ":" + accessTokenId;
  }

  public void deleteRefreshToken(UUID userId, String refreshToken) {
    String key = getRefreshKey(userId);

    // RefreshToken 삭제
    redisTemplate.opsForZSet().remove(key, refreshToken);
  }

  public void registerBlacklist(String accessTokenId, Duration remaining) {
    String blackListKey = blacklistKey(accessTokenId);

    // AccessToken BlackList 추가
    if (!remaining.isNegative() && !remaining.isZero()) {
      redisTemplate.opsForValue().set(blackListKey, "logout", remaining);
    }
  }

  private String blacklistKey(String accessTokenId) {
    return blacklistPrefix + accessTokenId;
  }

  // TODO: 기본구현 후 LuaScript를 통한 원자적 처리 구현
  public RotationResult rotateRefreshToken(
      UUID userId, String refreshToken, String newRefreshToken) {
    String key = getRefreshKey(userId);

    // 값이 있으면 double값, 없으면 null. O(1)
    if (redisTemplate.opsForZSet().score(key, refreshToken) == null) {
      // 키값 전체삭제
      redisTemplate.delete(key);
      return RotationResult.COMPROMISED;
    }

    long tokenExpirationTime =
        System.currentTimeMillis() + properties.refreshTokenExpiration().toMillis();

    redisTemplate.opsForZSet().remove(key, refreshToken);
    // 순서있는 Set(만료시간을 기준으로 정렬됨)
    redisTemplate.opsForZSet().add(key, newRefreshToken, tokenExpirationTime);
    // 토큰키 값 TTL 최신화
    redisTemplate.expire(key, properties.refreshTokenExpiration());

    return RotationResult.OK;
  }

  public enum RotationResult {
    OK,
    COMPROMISED
  }

  public void deleteAllRefreshToken(UUID userId) {
    String refreshKey = getRefreshKey(userId);
    // RefreshToken 전체삭제
    redisTemplate.delete(refreshKey);
  }

  public void lockUser(UUID userId) {
    String refreshKey = getRefreshKey(userId);
    String lockKey = lockKey(userId);

    // TODO: 기본 구현 후, 원자적 처리

    // RefreshToken 전체삭제
    redisTemplate.delete(refreshKey);
    // AccessToken 만료시간만큼 TTL 설정
    redisTemplate.opsForValue().set(lockKey, "lock", properties.accessTokenExpiration());
  }

  public void unlockUser(UUID userId) {
    String lockKey = lockKey(userId);
    // userLockKey 삭제
    redisTemplate.delete(lockKey);
  }

  private String lockKey(UUID userId) {
    return userLockPrefix + userId.toString();
  }

  public AuthCheckResult checkAuthStatus(String accessTokenId, UUID userId) {
    try {
      String blacklistKey = blacklistKey(accessTokenId);
      String lockKey = lockKey(userId);

      boolean isBlacklisted = Objects.equals(redisTemplate.hasKey(blacklistKey), true);
      boolean isUserLocked = Objects.equals(redisTemplate.hasKey(lockKey), true);

      return new AuthCheckResult(isBlacklisted, isUserLocked);
    } catch (DataAccessException e) {
      log.error("[Redis] 인증상태 조회 중 네트워크 장애 발생: reason={}", e.getMessage(), e);
      throw new InternalAuthenticationServiceException("redis 장애로 요청을 처리할 수 없습니다.", e);
    }
  }

  public record AuthCheckResult(boolean isBlacklisted, boolean isUserLocked) {}

  public boolean registerTempPassword(UUID userId, String tempPassword, Duration expireAt) {
    try {
      String tempPwKey = tempPwKey(userId);
      redisTemplate.opsForValue().set(tempPwKey, tempPassword, expireAt);
      return true;
    } catch (Exception e) {
      log.error("[Redis] 임시 비밀번호 저장 실패: userId={}", userId, e);
      return false;
    }
  }

  public boolean deleteTempPassword(UUID userId) {
    try {
      String tempPwKey = tempPwKey(userId);
      if (!Objects.equals(redisTemplate.delete(tempPwKey), true)) {
        log.debug("[Redis] 이미 만료되어 키가 존재하지 않음: userId={}", userId);
      }
      return true;

    } catch (Exception e) {
      log.error("[Redis] 임시 비밀번호 삭제 실패: userId={}", userId, e);
      return false;
    }
  }

  public String getTempPassword(UUID userId) {
    try {
      String tempKey = tempPwKey(userId);
      // 값이 있으면 문자열 반환, 없으면 null
      return redisTemplate.opsForValue().get(tempKey);
    } catch (Exception e) {
      log.error("[Redis] 임시 비밀번호 반환 실패: userId={}", userId, e);
      return null;
    }
  }

  public boolean verifyAndUseTempPassword(UUID userId, String inputPassword) {
    try {
      String tempKey = tempPwKey(userId);
      Long result = redisTemplate.execute(RELEASE_SCRIPT, List.of(tempKey), inputPassword);
      return Objects.equals(result, 1L);
    } catch (Exception e) {
      log.error("[Redis] 임시 비밀번호 검증 실패: userId={}", userId);
      return false;
    }
  }

  private String tempPwKey(UUID userId) {
    return tempPwPrefix + userId.toString();
  }
}
