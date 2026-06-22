package com.team02.mopl.global.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 서비스 로직에서 직접 발생시키는 도메인 커스텀 예외 처리
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();

        ErrorResponse response = new ErrorResponse(
            e.getClass().getSimpleName(),
            errorCode.getMessage(),
            e.getDetails()
        );

        return ResponseEntity
            .status(errorCode.getStatus())
            .body(response);
    }

    // PathVariable, RequestParam 타입이 맞지 않을 때 발생하는 예외 처리
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
        MethodArgumentTypeMismatchException e
    ) {
        String parameter = e.getName();
        String value = String.valueOf(e.getValue());
        String details = parameter + " 값 '" + value + "' 이(가) 올바른 형식이 아닙니다.";

        ErrorResponse response = new ErrorResponse(
            e.getClass().getSimpleName(),
            "잘못된 요청입니다.",
            details
        );

        return ResponseEntity.badRequest().body(response);
    }

    // @Valid 검증 실패 시 발생하는 예외 처리
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
        MethodArgumentNotValidException e
    ) {
        String details = e.getBindingResult()
            .getFieldErrors()
            .stream()
            .findFirst()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .orElse("요청 값이 올바르지 않습니다.");

        ErrorResponse response = new ErrorResponse(
            e.getClass().getSimpleName(),
            "잘못된 요청입니다.",
            details
        );

        return ResponseEntity.badRequest().body(response);
    }
}