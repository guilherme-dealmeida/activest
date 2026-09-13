package com.primevest.exception;
import java.time.LocalDateTime;
import java.util.Map;
public record ErrorResponse(int status, String message, LocalDateTime timestamp, Map<String, String> errors) {}
