package com.team02.mopl.global.exception;

import java.time.Instant;

public record ErrorResponse(
    String exceptionName,
    String message,
    String details
) {
}