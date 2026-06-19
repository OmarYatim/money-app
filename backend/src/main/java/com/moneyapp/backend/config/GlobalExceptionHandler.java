package com.moneyapp.backend.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.server.ResponseStatusException;

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

  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException exception) {
    HttpStatusCode status = exception.getStatusCode();
    return ResponseEntity.status(status)
        .body(new ErrorResponse(errorCode(status), exception.getReason()));
  }

  @ExceptionHandler(WebClientRequestException.class)
  ResponseEntity<ErrorResponse> handleWebClientRequest(WebClientRequestException exception) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(new ErrorResponse("SERVICE_UNAVAILABLE", "Powens is temporarily unavailable"));
  }

  private String errorCode(HttpStatusCode status) {
    if (status.isSameCodeAs(HttpStatus.CONFLICT)) {
      return "CONFLICT";
    }
    if (status.isSameCodeAs(HttpStatus.UNAUTHORIZED)) {
      return "UNAUTHORIZED";
    }
    if (status.isSameCodeAs(HttpStatus.SERVICE_UNAVAILABLE)) {
      return "SERVICE_UNAVAILABLE";
    }
    if (status.is4xxClientError()) {
      return "BAD_REQUEST";
    }
    return "INTERNAL_ERROR";
  }

  public record ValidationErrorResponse(String code, Map<String, String> fields) {}

  public record ErrorResponse(String code, String message) {}
}
