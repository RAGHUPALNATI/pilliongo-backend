package com.raghu.pilliongo.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mail.MailException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("message", message));
    }

    // ---- Framework / infrastructure failures ----
    // Each of these used to fall through to the RuntimeException (400) or
    // generic Exception (500) handlers below, giving the wrong status code
    // and leaking raw framework messages to the user. Spring always picks
    // the most specific matching handler, so these win over the broad ones.

    // @PreAuthorize("hasRole('DRIVER')") failing, e.g. a rider calling
    // POST /api/rides/offer. Was a 400 "Access Denied".
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException ex) {
        return error(HttpStatus.FORBIDDEN, "You don't have permission to do this.");
    }

    // Ride.@Version mismatch: two people changed the same ride at the same
    // moment (e.g. two riders grabbing the last seat). The loser gets a
    // clear "try again" instead of a Hibernate stack-trace message.
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, String>> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return error(HttpStatus.CONFLICT, "Someone else just updated this ride. Please refresh and try again.");
    }

    // Missing body or broken JSON. Was a 500.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleUnreadable(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "Request body is missing or is not valid JSON.");
    }

    // e.g. GET /api/rides/abc where {id} must be a number. Was a 500.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return error(HttpStatus.BAD_REQUEST, "Invalid value for '" + ex.getName() + "'.");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, String>> handleMissingParam(MissingServletRequestParameterException ex) {
        return error(HttpStatus.BAD_REQUEST, "Missing required parameter '" + ex.getParameterName() + "'.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, String>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, "HTTP method " + ex.getMethod() + " is not supported here.");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(NoResourceFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "Endpoint not found.");
    }

    // Gmail/SMTP down or wrong MAIL_PASSWORD while sending an OTP. register()
    // is @Transactional, so the half-created user is rolled back and the
    // person can simply try again later.
    @ExceptionHandler(MailException.class)
    public ResponseEntity<Map<String, String>> handleMail(MailException ex) {
        log.error("Email sending failed", ex);
        return error(HttpStatus.SERVICE_UNAVAILABLE, "We couldn't send the email right now. Please try again in a few minutes.");
    }


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

    // Anything truly unexpected = a bug. Log the full stack trace for you,
    // but never send internal details (SQL, class names) to the browser.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        log.error("Unhandled exception", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong on our side. Please try again.");
    }
}
