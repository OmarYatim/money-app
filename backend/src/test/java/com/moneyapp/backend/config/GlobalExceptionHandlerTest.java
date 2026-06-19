package com.moneyapp.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClientRequestException;

class GlobalExceptionHandlerTest {

  @Test
  void webClientRequestExceptionReturnsSanitizedServiceUnavailableResponse() {
    GlobalExceptionHandler handler = new GlobalExceptionHandler();
    WebClientRequestException exception =
        new WebClientRequestException(
            new IOException("Connection reset by peer"),
            HttpMethod.GET,
            URI.create("https://powens.test/users/me/accounts"),
            HttpHeaders.EMPTY);

    ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
        handler.handleWebClientRequest(exception);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(response.getBody())
        .isEqualTo(
            new GlobalExceptionHandler.ErrorResponse(
                "SERVICE_UNAVAILABLE", "Powens is temporarily unavailable"));
  }
}
