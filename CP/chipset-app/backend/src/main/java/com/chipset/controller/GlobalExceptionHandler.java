package com.chipset.controller;

import com.chipset.model.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleBadRequest(IllegalArgumentException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleAny(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ex, request);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, Exception ex, HttpServletRequest request) {
        String message = bestMessage(ex);

        ApiError body = new ApiError(
                Instant.now().toString(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI()
        );

        log.error("API error {} {} -> {}: {}", request.getMethod(), request.getRequestURI(), status.value(), message, ex);
        return ResponseEntity.status(status).body(body);
    }

    private String bestMessage(Throwable ex) {
        if (ex == null) return "Unknown error";

        // Prefer the top-level message if it exists (we sometimes throw actionable IllegalStateException)
        String top = ex.getMessage();
        if (top != null && !top.isBlank()) return top;

        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        String msg = root.getMessage();
        if (msg != null && !msg.isBlank()) return msg;

        return root.getClass().getSimpleName();
    }
}

