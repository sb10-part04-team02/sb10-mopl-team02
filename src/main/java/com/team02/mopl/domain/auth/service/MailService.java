package com.team02.mopl.domain.auth.service;

import com.team02.mopl.domain.auth.dto.ResetPasswordRequest;
import com.team02.mopl.domain.auth.jwt.JwtRegistry;
import com.team02.mopl.domain.user.entity.User;
import com.team02.mopl.domain.user.exception.UserNotFoundException;
import com.team02.mopl.domain.user.repository.UserRepository;
import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.global.exception.ErrorCode;
import com.team02.mopl.global.util.PasswordGenerator;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

  private final UserRepository userRepository;
  private final JavaMailSender mailSender;
  private final PasswordGenerator passwordGenerator;
  private final JwtRegistry jwtRegistry;

  @Value("${spring.mail.username}")
  private String sender;

  private static final DateTimeFormatter formatter =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Seoul"));

  public void sendResetPasswordEmail(ResetPasswordRequest request) {
    User findUser =
        userRepository
            .findByEmailAndDeletedAtIsNull(request.email())
            .orElseThrow(UserNotFoundException::new);
    UUID userId = findUser.getId();

    // [a-zA-Z0-9]{20}
    String tempPassword;
    try {
      tempPassword = passwordGenerator.generateRandomPassword(20);
    } catch (Exception e) {
      log.error("패스워드 생성기 설정이 잘못되었습니다. reason={}", e.getMessage(), e);
      throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    Instant expireAt = Instant.now().plus(3, ChronoUnit.MINUTES);
    String formattedExpireAt = formatter.format(expireAt);

    // redis 임시패스워드 등록하기
    if (!jwtRegistry.registerTempPassword(findUser.getId(), tempPassword, Duration.ofMinutes(3))) {
      throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    // 메일보내기
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(sender);
    message.setTo(request.email());
    message.setSubject("임시비밀번호 발급 - 모두의 플리");
    message.setText(
        "발급된 임시비밀번호는 >> " + tempPassword + " << 이고\n만료시간은 KST " + formattedExpireAt + "입니다");

    try {
      mailSender.send(message);
    } catch (MailException e) {
      log.error("[Mail] 임시비밀번호 이메일 발송 실패: userId={}", userId, e);

      if (!jwtRegistry.deleteTempPassword(userId)) {
        log.error("[Redis] 보상 트랜잭션 삭제 실패. 3분뒤 만료 TTL에 의존합니다: userId={}", userId);
      }

      throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
    }
  }
}
