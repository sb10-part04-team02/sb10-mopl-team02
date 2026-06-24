package com.team02.mopl.global.util;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.global.exception.ErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OwnershipValidatorTest {

  @Test
  @DisplayName("소유자와 요청자가 같으면 예외가 발생하지 않는다")
  void validateOwner_sameId_doesNotThrow() {
    UUID id = UUID.randomUUID();

    assertThatCode(() -> OwnershipValidator.validateOwner(id, id)).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("소유자와 요청자가 다르면 FORBIDDEN 예외가 발생한다")
  void validateOwner_differentId_throwsForbidden() {
    UUID ownerId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();

    assertThatThrownBy(() -> OwnershipValidator.validateOwner(ownerId, requesterId))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.FORBIDDEN);
  }

  @Test
  @DisplayName("소유자 ID가 null이면 FORBIDDEN 예외가 발생한다")
  void validateOwner_nullOwner_throwsForbidden() {
    assertThatThrownBy(() -> OwnershipValidator.validateOwner(null, UUID.randomUUID()))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.FORBIDDEN);
  }

  @Test
  @DisplayName("요청자 ID가 null이면 FORBIDDEN 예외가 발생한다")
  void validateOwner_nullRequester_throwsForbidden() {
    assertThatThrownBy(() -> OwnershipValidator.validateOwner(UUID.randomUUID(), null))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.FORBIDDEN);
  }
}
