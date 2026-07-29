package com.team02.mopl.domain.auth.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.team02.mopl.domain.auth.dto.ResetPasswordRequest;
import com.team02.mopl.domain.auth.jwt.JwtRegistry;
import com.team02.mopl.domain.user.entity.User;
import com.team02.mopl.domain.user.entity.enums.Role;
import com.team02.mopl.domain.user.exception.UserNotFoundException;
import com.team02.mopl.domain.user.repository.UserRepository;
import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.global.util.PasswordGenerator;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MailServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private JavaMailSender mailSender;
  @Mock private PasswordGenerator passwordGenerator;
  @Mock private JwtRegistry jwtRegistry;
  @InjectMocks private MailService mailService;

  @Nested
  class SendResetPasswordEmail {

    String email;
    User findUser;
    String tempPassword;

    @BeforeEach
    void setUp() {
      email = "example@gmail.com";
      findUser = new User("이름", email, "password", null, Role.USER, false);
      ReflectionTestUtils.setField(findUser, "id", UUID.randomUUID());
      tempPassword = "tempPassword";
    }

    @Test
    @DisplayName("유저를 찾지 못하면 예외를 던진다")
    void fail_shouldThrowException_whenUserNotFound() {
      // given
      ResetPasswordRequest request = new ResetPasswordRequest("notfound@gmail.com");
      given(userRepository.findByEmailAndDeletedAtIsNull(anyString())).willReturn(Optional.empty());

      // when & then
      assertThrows(UserNotFoundException.class, () -> mailService.sendResetPasswordEmail(request));
    }

    @Test
    @DisplayName("패스워드 생성기에서 예외가 생기면 예외를 던진다")
    void fail_shouldThrowException_whenPasswordGeneratorThrowsException() {
      // given
      ResetPasswordRequest request = new ResetPasswordRequest(email);
      given(userRepository.findByEmailAndDeletedAtIsNull(anyString()))
          .willReturn(Optional.of(findUser));
      given(passwordGenerator.generateRandomPassword(20)).willThrow(IllegalArgumentException.class);

      // when & then
      assertThrows(BusinessException.class, () -> mailService.sendResetPasswordEmail(request));
    }

    @Test
    @DisplayName("redis에서 예외가 생겨 false를 반환하면 예외를 던진다")
    void fail_shouldThrowException_whenRegisterTempPasswordThrowsException() {
      // given
      ResetPasswordRequest request = new ResetPasswordRequest(email);
      given(userRepository.findByEmailAndDeletedAtIsNull(anyString()))
          .willReturn(Optional.of(findUser));
      given(passwordGenerator.generateRandomPassword(20)).willReturn(tempPassword);

      given(jwtRegistry.registerTempPassword(any(UUID.class), anyString(), any(Duration.class)))
          .willReturn(false);

      // when & then
      assertThrows(BusinessException.class, () -> mailService.sendResetPasswordEmail(request));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("메일 발송 실패 시 보상 트랜잭션의 성공 여부와 상관없이 예외를 던진다")
    void fail_shouldThrowException_whenMailSendFailsAndCompensationResultIsProvided(
        boolean isCompensated) {
      // given
      ResetPasswordRequest request = new ResetPasswordRequest(email);
      given(userRepository.findByEmailAndDeletedAtIsNull(anyString()))
          .willReturn(Optional.of(findUser));
      given(passwordGenerator.generateRandomPassword(20)).willReturn(tempPassword);

      given(jwtRegistry.registerTempPassword(any(UUID.class), anyString(), any(Duration.class)))
          .willReturn(true);

      willThrow(MailSendException.class).given(mailSender).send(any(SimpleMailMessage.class));
      given(jwtRegistry.deleteTempPassword(any(UUID.class))).willReturn(isCompensated);

      // when & then
      assertThrows(BusinessException.class, () -> mailService.sendResetPasswordEmail(request));
    }

    @Test
    @DisplayName("요청이 들어오면 메일발송을 보낸다")
    void success_shouldSendRequester_whenResetPasswordRequestIsProvided() {
      // given
      ResetPasswordRequest request = new ResetPasswordRequest(email);
      given(userRepository.findByEmailAndDeletedAtIsNull(anyString()))
          .willReturn(Optional.of(findUser));
      given(passwordGenerator.generateRandomPassword(20)).willReturn(tempPassword);

      given(jwtRegistry.registerTempPassword(any(UUID.class), anyString(), any(Duration.class)))
          .willReturn(true);

      willDoNothing().given(mailSender).send(any(SimpleMailMessage.class));

      // when & then
      assertDoesNotThrow(() -> mailService.sendResetPasswordEmail(request));
      then(mailSender).should(times(1)).send(any(SimpleMailMessage.class));
      then(jwtRegistry).should(never()).deleteTempPassword(any(UUID.class));
    }
  }
}
