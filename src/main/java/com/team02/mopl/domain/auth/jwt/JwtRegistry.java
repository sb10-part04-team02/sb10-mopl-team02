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
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtRegistry {

  @Value("${app.jwt.redis.refresh-prefix}")
  private String refreshPrefix;

  @Value("${app.jwt.redis.used-refresh-prefix}")
  private String usedRefreshPrefix;

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
  private static final RedisScript<Long> ROTATE_SCRIPT =
      new DefaultRedisScript<>(
          """
          local refreshKey = KEYS[1]
          local usedKey = KEYS[2]
          local accessKey = KEYS[3]

          local oldRefreshToken = ARGV[1]
          local newRefreshToken = ARGV[2]
          local newAccessToken = ARGV[3]

          local now = tonumber(ARGV[4])
          local refreshExpirationMillis = tonumber(ARGV[5])
          local tokenExpirationTime = tonumber(ARGV[6])
          local remainingAccessMillis = tonumber(ARGV[7])

          -- 탈취된 경우 refreshKey, accessKey, usedKey 삭제
          if redis.call('SISMEMBER', usedKey, oldRefreshToken) == 1 then
              redis.call('DEL', refreshKey, usedKey, accessKey)
              return 2
          end

          -- 만료되거나 로그아웃해서 사라진 경우
          if not redis.call('ZSCORE', refreshKey, oldRefreshToken) then
              return 1
          end

          -- 기존토큰 제거 및 usedToken에 기록
          redis.call('ZREMRANGEBYSCORE', refreshKey, 0, now)
          redis.call('ZREM', refreshKey, oldRefreshToken)
          redis.call('SADD', usedKey, oldRefreshToken)

          if remainingAccessMillis > 0 then
              redis.call('PEXPIRE', usedKey, remainingAccessMillis)
          end

          -- 순서있는 Set(만료시간을 기준으로 정렬됨)
          redis.call('ZADD', refreshKey, tokenExpirationTime, newRefreshToken)
          redis.call('PEXPIRE', refreshKey, refreshExpirationMillis)

          -- access 토큰 저장
          if remainingAccessMillis > 0 then
              redis.call('PSETEX', accessKey, remainingAccessMillis, newAccessToken)
          end

          return 0
          """,
          Long.class);
  private static final RedisScript<List> AUTH_STATUS_SCRIPT =
      new DefaultRedisScript<>(
          """
          local isBlacklisted = redis.call('EXISTS', KEYS[1])
          local isUserLocked = redis.call('EXISTS', KEYS[2])
          local storedAccessToken = redis.call('GET', KEYS[3])

          -- 저장된 토큰이 존재하고, 요청으로 들어온 토큰과 정확히 일치하는지 확인
          local isAccessTokenActive = 0
          if storedAccessToken and storedAccessToken == ARGV[1] then
              isAccessTokenActive = 1
          end

          return { isBlacklisted, isUserLocked, isAccessTokenActive }
          """,
          List.class);
  private static final RedisScript<Long> DELETE_ALL_TOKENS_SCRIPT =
      new DefaultRedisScript<>("return redis.call('DEL', KEYS[1], KEYS[2])", Long.class);
  private static final RedisScript<String> LOCK_USER_SCRIPT =
      new DefaultRedisScript<>(
          """
          redis.call('DEL', KEYS[1])
          redis.call('SETEX', KEYS[2], ARGV[2], ARGV[1])
          return 'OK'
          """,
          String.class);

  public void registerToken(UUID userId, String refreshToken, String accessToken) {
    String refreshKey = getRefreshKey(userId);
    String accessKey = getActiveAccessKey(userId);

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

  private String getActiveAccessKey(UUID userId) {
    return activeAccessPrefix + userId.toString();
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

  public RotationResult rotateRefreshToken(
      UUID userId, String refreshToken, String newRefreshToken, String newAccessToken) {
    String refreshKey = getRefreshKey(userId);
    String usedKey = getUsedRefreshPrefix(userId);
    String accessKey = getActiveAccessKey(userId);

    long now = System.currentTimeMillis();
    long refreshExpirationMillis = properties.refreshTokenExpiration().toMillis();
    long tokenExpirationTime = now + refreshExpirationMillis;

    // Access Token 남은 만료 시간
    Duration remaining = jwtUtils.getRemainingTimeToExpiration(newAccessToken);
    long remainingAccessMillis = remaining.isNegative() ? 0 : remaining.toMillis();

    try {
      Long resultCode =
          redisTemplate.execute(
              ROTATE_SCRIPT,
              List.of(refreshKey, usedKey, accessKey),
              refreshToken,
              newRefreshToken,
              newAccessToken,
              String.valueOf(now),
              String.valueOf(refreshExpirationMillis),
              String.valueOf(tokenExpirationTime),
              String.valueOf(remainingAccessMillis));

      if (resultCode == null) {
        return RotationResult.INVALID;
      }

      return switch (resultCode.intValue()) {
        case 0 -> RotationResult.OK;
        case 1 -> RotationResult.INVALID;
        default -> RotationResult.COMPROMISED;
      };
    } catch (DataAccessException e) {
      log.error("[Redis] 토큰 rotation 진행 중 네트워크/redis 장애 발생 - userId: {}", userId, e);
      throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
    }
  }

  public enum RotationResult {
    OK,
    INVALID,
    COMPROMISED
  }

  private String getUsedRefreshPrefix(UUID userId) {
    return usedRefreshPrefix + userId.toString();
  }

  public void deleteAllRefreshToken(UUID userId) {
    String refreshKey = getRefreshKey(userId);
    // RefreshToken 전체삭제
    redisTemplate.delete(refreshKey);
  }

  public void lockUser(UUID userId) {
    String refreshKey = getRefreshKey(userId);
    String lockKey = lockKey(userId);
    long ttlSeconds = properties.accessTokenExpiration().toSeconds();

    try {
      redisTemplate.execute(
          LOCK_USER_SCRIPT, List.of(refreshKey, lockKey), "lock", String.valueOf(ttlSeconds));
    } catch (DataAccessException e) {
      log.error("[Redis] 유저 잠금 처리 실패: userId={}", userId, e);
      throw e;
    }
  }

  public void unlockUser(UUID userId) {
    String lockKey = lockKey(userId);
    // userLockKey 삭제
    redisTemplate.delete(lockKey);
  }

  private String lockKey(UUID userId) {
    return userLockPrefix + userId.toString();
  }

  public AuthCheckResult checkAuthStatus(String accessTokenId, UUID userId, String accessToken) {
    try {
      String blacklistKey = blacklistKey(accessTokenId);
      String lockKey = lockKey(userId);
      String accessKey = getActiveAccessKey(userId);

      List<Long> result =
          redisTemplate.execute(
              AUTH_STATUS_SCRIPT, List.of(blacklistKey, lockKey, accessKey), accessToken);

      if (result == null || result.size() < 3) {
        log.error("[Redis] Redis스크립트 실행결과가 올바르지 않음 - userId: {}", userId);
        throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
      }

      boolean isBlacklisted = result.get(0) == 1L;
      boolean isUserLocked = result.get(1) == 1L;
      boolean isAccessTokenActive = result.get(2) == 1L;

      return new AuthCheckResult(isBlacklisted, isUserLocked, isAccessTokenActive);
    } catch (DataAccessException e) {
      log.error("[Redis] 인증상태 조회 중 네트워크 장애 발생: reason={}", e.getMessage(), e);
      throw new InternalAuthenticationServiceException("redis 장애로 요청을 처리할 수 없습니다.", e);
    }
  }

  public record AuthCheckResult(
      boolean isBlacklisted, boolean isUserLocked, boolean isAccessTokenActive) {}

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

  public void deleteAllToken(UUID userId) {
    String accessKey = getActiveAccessKey(userId);
    String refreshKey = getRefreshKey(userId);

    try {
      redisTemplate.execute(DELETE_ALL_TOKENS_SCRIPT, List.of(accessKey, refreshKey));
    } catch (Exception e) {
      log.error("[Redis] 액세스 토큰 삭제 실패: userId={}", userId, e);
      throw e;
    }
  }
}
