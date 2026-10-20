package com.example.demo.controller;

import com.example.demo.exception.MenuItemNotFoundException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Обработка ошибок ТОЛЬКО для MenuController (assignableTypes),
 * чтобы не конфликтовать с общим обработчиком, если он появится у Шаха.
 */
@RestControllerAdvice(assignableTypes = MenuController.class)
public class MenuExceptionHandler {

    @ExceptionHandler(MenuItemNotFoundException.class)
    public ProblemDetail handleNotFound(MenuItemNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException e) {
        String details = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, details);
    }
}
