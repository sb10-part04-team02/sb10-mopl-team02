package com.team02.mopl.global.exception;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  // 서비스 로직에서 직접 발생시키는 도메인 커스텀 예외 처리
  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
    ErrorCode errorCode = e.getErrorCode();

    ErrorResponse response =
        new ErrorResponse(
            e.getClass().getSimpleName(), errorCode.getMessage(), Map.of("reason", e.getDetails()));

    return ResponseEntity.status(errorCode.getStatus()).body(response);
  }

  // PathVariable, RequestParam 타입이 맞지 않을 때 발생하는 예외 처리
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
    String parameter = e.getName();
    String value = String.valueOf(e.getValue());
    String details = parameter + " 값 '" + value + "' 이(가) 올바른 형식이 아닙니다.";

    ErrorResponse response =
        new ErrorResponse(e.getClass().getSimpleName(), "잘못된 요청입니다.", Map.of(parameter, details));

    return ResponseEntity.badRequest().body(response);
  }

  // @Valid 검증 실패 시 발생하는 예외 처리
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidationException(
      MethodArgumentNotValidException e) {
    Map<String, String> details = new LinkedHashMap<>();

    e.getBindingResult()
        .getFieldErrors()
        .forEach(error -> details.putIfAbsent(error.getField(), error.getDefaultMessage()));

    ErrorResponse response = new ErrorResponse(e.getClass().getSimpleName(), "잘못된 요청입니다.", details);

    return ResponseEntity.badRequest().body(response);
  }

  // 예상하지 못한 서버 내부 오류 처리
  // 스택 트레이스는 서버 로그에만 기록
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleException(Exception e) {
    log.error("Unexpected server error", e);

    ErrorResponse response =
        new ErrorResponse(
            "InternalServerException", "서버 내부 오류가 발생했습니다.", Map.of("reason", "관리자에게 문의해주세요."));

    return ResponseEntity.internalServerError().body(response);
  }
}
