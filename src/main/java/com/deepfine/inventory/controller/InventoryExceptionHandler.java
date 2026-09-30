package com.deepfine.inventory.controller;

import com.deepfine.inventory.exception.InventoryRuleViolationException;
import com.deepfine.inventory.exception.ProductNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class InventoryExceptionHandler {

    @ExceptionHandler(InventoryRuleViolationException.class)
    public ResponseEntity<ProblemDetail> handleConflict(InventoryRuleViolationException exception) {
        return problem(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(ProductNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(
            MethodArgumentNotValidException exception) {
        return problem(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다.");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadable(
            HttpMessageNotReadableException exception) {
        return problem(HttpStatus.BAD_REQUEST, "요청 본문이 올바르지 않습니다.");
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        return ResponseEntity.status(status).body(body);
    }
}
