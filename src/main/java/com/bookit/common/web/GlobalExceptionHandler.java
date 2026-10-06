package com.bookit.common.web;

import com.bookit.catalogue.domain.exception.DuplicateEntityException;
import com.bookit.catalogue.domain.exception.ResourceNotFoundException;
import com.bookit.catalogue.domain.exception.SeatMapImmutableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleResourceNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of("RESOURCE_NOT_FOUND", ex.getMessage(), HttpStatus.NOT_FOUND.value()));
    }

    // 1. Write handler for DuplicateEntityException (HTTP 409 CONFLICT)
    @ExceptionHandler(DuplicateEntityException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateEntityException(DuplicateEntityException ex) {
        log.warn("Duplicate entity: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of("DUPLICATE_ENTITY", ex.getMessage(), HttpStatus.CONFLICT.value()));
    }

    // 2. Write handler for SeatMapImmutableException (HTTP 409 CONFLICT)
    @ExceptionHandler(SeatMapImmutableException.class)
    public ResponseEntity<ApiErrorResponse> handleSeatMapImmutableException(SeatMapImmutableException ex) {
        log.warn("Seat Map Immutable: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of("SEAT_MAP_IMMUTABLE", ex.getMessage(), HttpStatus.CONFLICT.value()));
    }

    // 3. Write handler for IllegalStateException (HTTP 400 BAD_REQUEST)
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalStateError(IllegalStateException ex) {
        log.warn("Illegal state: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of("ILLEGAL_STATE", ex.getMessage(), HttpStatus.BAD_REQUEST.value()));
    }

    // 4. Write handler for MethodArgumentNotValidException (extract field errors into List<ApiErrorResponse.ValidationError>)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        List<ApiErrorResponse.ValidationError> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                        .map(fieldError -> new ApiErrorResponse.ValidationError(
                                fieldError.getField(),
                                fieldError.getDefaultMessage()
                        ))
                                .toList();
        log.warn("Method argument not valid: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.withValidationErrors(
                        "VALIDATION_FAILED",
                        "Request validation failed",
                        HttpStatus.BAD_REQUEST.value(),
                        errors
                ));
    }

    // 5. Handle Spring Security AccessDeniedException (HTTP 403 FORBIDDEN)
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(org.springframework.security.access.AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiErrorResponse.of("FORBIDDEN", "Access denied: insufficient permissions", HttpStatus.FORBIDDEN.value()));
    }

    // 6. Handle Spring Security AuthenticationException (HTTP 401 UNAUTHORIZED)
    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthentication(org.springframework.security.core.AuthenticationException ex) {
        log.warn("Authentication failed: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiErrorResponse.of("UNAUTHORIZED", "Authentication required", HttpStatus.UNAUTHORIZED.value()));
    }

    // 7. Fallback handler for generic Exception.class (HTTP 500)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleException(Exception ex) {
        log.error("Unhandled internal server error", ex); // 👈 Use log.error + pass 'ex' to print stack trace
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.of(
                        "INTERNAL_SERVER_ERROR",
                        "An unexpected error occurred. Please contact support.", // 👈 Safe client message
                        HttpStatus.INTERNAL_SERVER_ERROR.value()
                ));
    }
}