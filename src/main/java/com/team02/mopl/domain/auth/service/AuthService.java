package com.team02.mopl.domain.auth.service;

import com.team02.mopl.domain.auth.dto.JwtDto;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

  public TokenResult update(String refreshToken) {
    return null;
  }

  public record TokenResult(JwtDto jwtDto, String refreshToken) {}
}
