package com.team02.mopl.global.exception;

import java.util.Map;

public record ErrorResponse(String exceptionName, String message, Map<String, String> details) {}
