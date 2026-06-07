package com.moneyapp.backend.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  ValidationErrorResponse handleValidation(MethodArgumentNotValidException exception) {
    Map<String, String> fields = new LinkedHashMap<>();
    exception
        .getBindingResult()
        .getFieldErrors()
        .forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
    exception
        .getBindingResult()
        .getGlobalErrors()
        .forEach(error -> fields.putIfAbsent(error.getObjectName(), error.getDefaultMessage()));

    return new ValidationErrorResponse("VALIDATION_ERROR", fields);
  }

  public record ValidationErrorResponse(String code, Map<String, String> fields) {}
}
