package com.vellum.auth.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // Handles custom application exceptions
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<Map<String, Object>> handleCustomException(
            CustomException ex) {
        log.error("CustomException: {} ({})", ex.getMessage(), ex.getStatus());
        return ResponseEntity
                .status(ex.getStatus())
                .body(buildErrorBody(ex.getMessage(), ex.getStatus().value()));
    }

    // Handle validation errors (@Valid failed)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(
            MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }

        Map<String, Object> body = buildErrorBody("Validation failed", 400);
        body.put("fieldErrors", fieldErrors);

        log.error("Validation failed: {}", fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    // Handle unexpected errors
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(
            Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildErrorBody(
                        "An unexpected error occurred. Please try again.",
                        500
                ));
    }

    // Helper to build consistent error response body
    private Map<String, Object> buildErrorBody(String message, int status) {
        Map<String, Object> body = new HashMap<>();
        body.put("error",     message);
        body.put("status",    status);
        body.put("timestamp", LocalDateTime.now().toString());
        return body;
    }
}