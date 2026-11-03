package com.deshko.orderservice.exception;

import com.deshko.orderservice.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.ErrorResponse;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler({OrderNotFoundException.class, ItemNotFoundException.class,
            UserNotFoundException.class})
    public ResponseEntity<ApiError> handleNotFound(RuntimeException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler({InvalidOrderStatusTransitionException.class, OrderNotEditableException.class,
            ItemInUseException.class})
    public ResponseEntity<ApiError> handleConflict(RuntimeException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException ex,
                                                    HttpServletRequest request) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, "Data conflict", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex,
                                                     HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e ->
                errors.merge(e.getField(), String.valueOf(e.getDefaultMessage()),
                        (a, b) -> a + "; " + b));
        return ResponseEntity.badRequest()
                .body(error(HttpStatus.BAD_REQUEST, "Validation failed", request, errors));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class})
    public ResponseEntity<ApiError> handleBadRequest(Exception ex, HttpServletRequest request) {
        String message = ex instanceof IllegalArgumentException
                ? ex.getMessage() : "Malformed request or invalid parameter value";
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex,
                                                       HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "Access denied", request);
    }

    @ExceptionHandler(UserServiceClientException.class)
    public ResponseEntity<ApiError> handleUserServiceClient(UserServiceClientException ex,
                                                            HttpServletRequest request) {
        if (ex.getStatus() == 403) {
            return build(HttpStatus.FORBIDDEN, "Access denied", request);
        }
        log.warn("User service rejected request: {}", ex.getMessage());
        return build(HttpStatus.BAD_GATEWAY, "User service rejected the request", request);
    }

    @ExceptionHandler(UserServiceUnavailableException.class)
    public ResponseEntity<ApiError> handleUserServiceUnavailable(UserServiceUnavailableException ex,
                                                                 HttpServletRequest request) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, "User service is temporarily unavailable", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleOther(Exception ex, HttpServletRequest request) {
        if (ex instanceof ErrorResponse errorResponse) {
            return build(errorResponse.getStatusCode(),
                    errorResponse.getBody().getDetail(), request);
        }
        log.error("Unexpected error on {}", request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", request);
    }

    private ResponseEntity<ApiError> build(HttpStatusCode status, String message,
                                           HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(error(status, message, request, null));
    }

    private ApiError error(HttpStatusCode status, String message,
                           HttpServletRequest request, Map<String, String> details) {
        String reason = (status instanceof HttpStatus hs) ? hs.getReasonPhrase() : "";
        String safeMessage = (message != null && !message.isBlank()) ? message : reason;
        return new ApiError(LocalDateTime.now(), status.value(), reason, safeMessage,
                request.getRequestURI(), details
        );
    }
}
