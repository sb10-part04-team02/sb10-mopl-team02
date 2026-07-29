package com.team02.mopl.global.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PasswordGeneratorTest {

  @InjectMocks private PasswordGenerator passwordGenerator;

  @Test
  @DisplayName("입력값이 1 미만이면 예외를 던진다")
  void fail_shouldThrowException_whenLengthIsLessThanOne() {
    // when & then
    assertThrows(IllegalArgumentException.class, () -> passwordGenerator.generateRandomPassword(0));
  }

  @Test
  @DisplayName("유효한 길이 수치가 들어오면 일치하는 길이의 랜덤값을 반환한다")
  void success_shouldReturnRandomValueWithRequestedLength_whenLengthIsValid() {
    // given
    int size = 20;

    // when
    String actual = passwordGenerator.generateRandomPassword(size);

    // then
    assertThat(actual.length()).isEqualTo(size);
  }
}
