package com.restaurant.ordering.common.exception;
import com.restaurant.ordering.common.api.Problems;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import java.util.*;

@RestControllerAdvice @Slf4j
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<Object> business(ApiException e, HttpServletRequest r) { return response(e.getStatus(), e.getCode(), e.getMessage(), r); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Object> constraint(DataIntegrityViolationException e, HttpServletRequest r) {
        return response(HttpStatus.CONFLICT, "DATA_CONFLICT", "Operation violates a database constraint", r);
    }
    @ExceptionHandler({PessimisticLockingFailureException.class, OptimisticLockingFailureException.class})
    ResponseEntity<Object> concurrency(Exception e, HttpServletRequest r) {
        return response(HttpStatus.CONFLICT, "CONCURRENT_UPDATE", "Concurrent update; retry the operation with the same key", r);
    }
    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<Object> authentication(Exception e, HttpServletRequest r) { return response(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid credentials or expired token", r); }
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Object> forbidden(Exception e, HttpServletRequest r) { return response(HttpStatus.FORBIDDEN, "FORBIDDEN", "Insufficient permissions", r); }
    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<Object> validation(Exception e, HttpServletRequest r) { return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid request parameters", r); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> unexpected(Exception e, HttpServletRequest r) {
        log.error("Unhandled server error path={}", r.getRequestURI(), e);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected server error", r);
    }
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e, HttpHeaders h, HttpStatusCode s, WebRequest r) {
        var problem = Problems.create(s, "VALIDATION_ERROR", "Request validation failed", path(r));
        problem.setProperty("errors", e.getBindingResult().getFieldErrors().stream()
            .map(f -> Map.of("field", f.getField(), "message", Objects.requireNonNullElse(f.getDefaultMessage(), "invalid"))).toList());
        return ResponseEntity.status(s).headers(h).body(problem);
    }
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception e, Object body, HttpHeaders h, HttpStatusCode s, WebRequest r) {
        return ResponseEntity.status(s).headers(h).body(Problems.create(s, "INVALID_REQUEST", "Request could not be processed", path(r)));
    }
    private String path(WebRequest r) { return ((ServletWebRequest) r).getRequest().getRequestURI(); }
    private ResponseEntity<Object> response(HttpStatus s, String code, String detail, HttpServletRequest r) {
        return ResponseEntity.status(s).body(Problems.create(s, code, detail, r.getRequestURI()));
    }
}
