package com.team02.mopl.domain.content.ingestion.exception;

import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.global.exception.ErrorCode;

// 수집 실행 락을 이미 누군가(스케줄 실행/다른 인스턴스/직전 수동 요청) 쥐고 있어 수동 수집을 거절할 때 사용
public class IngestionAlreadyRunningException extends BusinessException {

  public IngestionAlreadyRunningException() {
    super(ErrorCode.INGESTION_ALREADY_RUNNING);
  }
}
