package com.raghu.pilliongo.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // Thrown by @Valid on a @RequestBody DTO (RegisterRequest, LoginRequest,
    // RideRequest, OtpVerifyRequest, ProfileUpdateRequest) when one or more
    // Bean Validation constraints fail. This isn't a RuntimeException, so
    // without this handler it would fall through to the generic Exception
    // handler below and come back as a 500 instead of a 400 that actually
    // says which fields are wrong.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException ex) {
        // Collect every failed field, not just the first one, so the client
        // can show all problems at once instead of one-at-a-time.
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fieldError ->
                fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage()));

        String summary = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));

        Map<String, Object> body = new HashMap<>();
        body.put("message", summary.isBlank() ? "Invalid input parameters" : summary);
        body.put("errors", fieldErrors);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(body);
    }

    // Only thrown by AuthService.forgotPassword() when the email isn't
    // registered — every other "not found"/business-rule failure in this
    // app throws a plain RuntimeException and stays a 400 (handled below),
    // unchanged.
    @ExceptionHandler(EmailNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleEmailNotFound(EmailNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntimeException(RuntimeException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", ex.getMessage() != null ? ex.getMessage() : "An error occurred"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgumentException(IllegalArgumentException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", ex.getMessage() != null ? ex.getMessage() : "Invalid input parameters"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", ex.getMessage() != null ? ex.getMessage() : "An unexpected server error occurred"));
    }
}
