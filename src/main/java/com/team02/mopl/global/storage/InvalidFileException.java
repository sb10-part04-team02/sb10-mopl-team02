package com.team02.mopl.global.storage;

import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.global.exception.ErrorCode;

// 업로드 파일 검증 실패 (허용되지 않은 형식/확장자 불일치/크기 초과)
public class InvalidFileException extends BusinessException {

  public InvalidFileException(String reason) {
    super(ErrorCode.INVALID_REQUEST);
    addDetail("file", reason);
  }
}
