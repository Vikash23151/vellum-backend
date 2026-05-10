package com.vellum.media.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<Map<String, Object>> handleCustom(
            CustomException ex) {
        log.error("{}", ex.getMessage());
        return ResponseEntity.status(ex.getStatus())
                .body(buildError(ex.getMessage(),
                        ex.getStatus().value()));
    }

    /*
     * MaxUploadSizeExceededException:
     * Thrown by Spring BEFORE our service is called
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxSize(
            MaxUploadSizeExceededException ex) {
        log.error("File too large: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildError(
                        "File size exceeds the maximum limit of 10MB",
                        400));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError e : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(e.getField(), e.getDefaultMessage());
        }
        Map<String, Object> body = buildError("Validation failed", 400);
        body.put("fieldErrors", fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(
            Exception ex) {
        log.error("Unexpected: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildError("Unexpected error occurred", 500));
    }

    private Map<String, Object> buildError(String message,
                                           int status) {
        Map<String, Object> body = new HashMap<>();
        body.put("error",     message);
        body.put("status",    status);
        body.put("timestamp", LocalDateTime.now().toString());
        return body;
    }
}