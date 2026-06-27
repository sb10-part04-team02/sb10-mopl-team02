package com.team02.mopl.domain.auth.jwt;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JwtRegistry {

  private final JwtTokenProvider jwtTokenProvider;
  private final StringRedisTemplate redisTemplate;
}
