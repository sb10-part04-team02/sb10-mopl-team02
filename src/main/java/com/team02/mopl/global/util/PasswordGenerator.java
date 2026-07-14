package com.team02.mopl.global.util;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;
import org.springframework.stereotype.Component;

@Component
public class PasswordGenerator {

  private static final String CHAR_SET =
      "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
  private static final RandomGenerator random = new SecureRandom(); // 암호학적 안전한 난수생성기

  public String generateRandomPassword(int length) {
    if (length < 1) {
      throw new IllegalArgumentException("길이는 1 이상이어야 합니다");
    }

    StringBuilder sb = new StringBuilder(length);

    for (int i = 0; i < length; i++) {
      int randomIndex = random.nextInt(CHAR_SET.length());
      char randomChar = CHAR_SET.charAt(randomIndex);
      sb.append(randomChar);
    }

    return sb.toString();
  }
}
