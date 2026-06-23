package com.team02.mopl.global.util;

import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.global.exception.ErrorCode;
import java.util.UUID;

public final class OwnershipValidator {

  private OwnershipValidator() {}

  /** 요청자가 리소스 소유자가 아니면 FORBIDDEN. */
  public static void validateOwner(UUID ownerId, UUID requesterId) {
    if (ownerId == null || !ownerId.equals(requesterId)) {
      throw new BusinessException(ErrorCode.FORBIDDEN);
    }
  }
}
