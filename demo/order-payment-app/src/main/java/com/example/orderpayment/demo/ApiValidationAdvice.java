package com.example.orderpayment.demo;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** Convert generated path-parameter Bean Validation failures into HTTP 400. */
@RestControllerAdvice
public class ApiValidationAdvice {
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, String>> invalidPath(ConstraintViolationException failure) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", failure.getMessage()));
    }
}
