package com.team02.mopl.domain.auth.jwt;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtRegistry {

  @Value("${app.jwt.redis.refresh-prefix}")
  private String refreshPrefix;

  @Value("${app.jwt.redis.max-account-count}")
  private long maxAccountCount;

  @Value("${app.jwt.redis.blacklist-prefix}")
  private String blacklistPrefix;

  @Value("${app.jwt.redis.user-lock-prefix}")
  private String userLockPrefix;

  private final JwtProperties properties;
  private final StringRedisTemplate redisTemplate;

  // TODO: 기본구현 후 LuaScript를 통한 원자적 처리 구현
  public void registerRefreshToken(UUID userId, String refreshToken) {
    String key = refreshKey(userId);
    long now = System.currentTimeMillis();
    long tokenExpirationTime = now + properties.refreshTokenExpiration().toMillis();

    // 만료시간 토큰 지우기
    redisTemplate.opsForZSet().removeRangeByScore(key, 0, now);
    // 순서있는 Set(만료시간을 기준으로 정렬됨)
    redisTemplate.opsForZSet().add(key, refreshToken, tokenExpirationTime);
    // 개수제한
    Long currentCount = redisTemplate.opsForZSet().size(key);
    if (currentCount != null && currentCount > maxAccountCount) {
      long removeCount = currentCount - maxAccountCount;
      redisTemplate.opsForZSet().removeRange(key, 0, removeCount - 1);
    }
    // 토큰키 값 TTL 최신화
    redisTemplate.expire(key, properties.refreshTokenExpiration());
  }

  private String refreshKey(UUID userId) {
    return refreshPrefix + userId.toString();
  }

  public void deleteRefreshToken(UUID userId, String refreshToken) {
    String key = refreshKey(userId);

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

  //  // TODO: 삭제처리
  //  public boolean isBlacklisted(String accessTokenId) {
  //    String blackListKey = blacklistKey(accessTokenId);
  //    return Objects.equals(redisTemplate.hasKey(blackListKey), true);
  //  }

  private String blacklistKey(String accessTokenId) {
    return blacklistPrefix + accessTokenId;
  }

  // TODO: 기본구현 후 LuaScript를 통한 원자적 처리 구현
  public RotationResult rotateRefreshToken(
      UUID userId, String refreshToken, String newRefreshToken) {
    String key = refreshKey(userId);

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

  public void lockUser(UUID userId) {
    String refreshKey = refreshKey(userId);
    String lockKey = lockKey(userId);

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
      log.error("[Redis] 인증상태 조회 중 네트워크 장애 발생 - 기본값 반환", e);
      return new AuthCheckResult(false, false);
    }
  }

  public record AuthCheckResult(boolean isBlacklisted, boolean isUserLocked) {}
}
