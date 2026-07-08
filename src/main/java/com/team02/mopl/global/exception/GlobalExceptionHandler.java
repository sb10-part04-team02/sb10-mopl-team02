package com.team02.mopl.global.exception;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.team02.mopl.domain.follow.exception.NotFollowedException;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestCookieException;
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
        new ErrorResponse(e.getClass().getSimpleName(), errorCode.getMessage(), e.getDetails());

    return ResponseEntity.status(errorCode.getStatus()).body(response);
  }

  // 프로필용 팔로워 수 조회 응답에 필요한 예외입니다.
  @ExceptionHandler(NotFollowedException.class)
  public ResponseEntity<ErrorResponse> handleNotFollowedException(NotFollowedException e) {
    ErrorResponse response =
        new ErrorResponse("follow.not_followed", "팔로우하지 않은 사용자입니다.", e.getDetails());

    return ResponseEntity.status(e.getErrorCode().getStatus()).body(response);
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

  // @Valid 검증 실패 시 발생하는 예외 처리(BindException이 MethodArgumentNotValidException을 상속)
  @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
  public ResponseEntity<ErrorResponse> handleValidationException(BindException e) {
    Map<String, String> details = new LinkedHashMap<>();

    e.getBindingResult()
        .getFieldErrors()
        .forEach(error -> details.putIfAbsent(error.getField(), error.getDefaultMessage()));

    ErrorResponse response = new ErrorResponse(e.getClass().getSimpleName(), "잘못된 요청입니다.", details);

    return ResponseEntity.badRequest().body(response);
  }

  // 지원되지 않는 메서드 요청 예외 처리
  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ErrorResponse> handleHttpRequestMethodNotSupportedException(
      HttpRequestMethodNotSupportedException e) {
    ErrorResponse response =
        new ErrorResponse(
            e.getClass().getSimpleName(),
            "지원하지 않는 HTTP 메서드 요청입니다.",
            Map.of("reason", e.getMethod() + " 메서드는 이 엔드포인트에서 지원되지 않습니다."));

    return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(response);
  }

  // @RequestBody의 JsonBody 변환 실패시 나오는 예외
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(
      HttpMessageNotReadableException e) {

    String exceptionName = "InvalidRequestException";
    String message = "요청 본문(JSON) 변환에 실패하였습니다.";
    Map<String, String> details = Map.of("reason", "JSON 형식이 올바르지 않거나 본문이 비어있습니다.");

    if (e.getCause() instanceof InvalidFormatException invalidFormatEx) {
      String invalidValue = String.valueOf(invalidFormatEx.getValue());
      String type = invalidFormatEx.getTargetType().getSimpleName().toLowerCase();
      details = Map.of(type, invalidValue);
    }

    ErrorResponse response = new ErrorResponse(exceptionName, message, details);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
  }

  // 예상하지 못한 서버 내부 오류 처리
  // 스택 트레이스는 서버 로그에만 기록
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleException(Exception e) {

    String exceptionName = "InternalServerException";
    HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
    String message = "서버 내부 오류가 발생했습니다.";
    Map<String, String> details = Map.of("reason", "관리자에게 문의해주세요.");

    // 스프링 예외 추가
    if (e instanceof MissingRequestCookieException cookieEx) {
      exceptionName = "AuthenticationRequiredException";
      status = HttpStatus.UNAUTHORIZED;
      message = "인증 쿠키가 누락되었습니다.";
      details = Map.of(cookieEx.getCookieName(), "필수 인증 쿠키가 누락되었습니다");
    } else {
      log.error("Unexpected server error", e);
    }

    ErrorResponse response = new ErrorResponse(exceptionName, message, details);
    return ResponseEntity.status(status).body(response);
  }
}
