package com.moneyapp.backend.banking.service;

import com.moneyapp.backend.banking.dto.PowensAccessTokenResponse;
import com.moneyapp.backend.banking.dto.PowensTokenCodeResponse;
import com.moneyapp.backend.config.PowensProperties;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@RequiredArgsConstructor
class WebClientPowensClient implements PowensClient {

  private final WebClient powensWebClient;
  private final PowensProperties powensProperties;

  @Override
  public PowensAccessTokenResponse createUserAccessToken() {
    return powensWebClient
        .post()
        .uri("/auth/token/access")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + powensProperties.manageToken())
        .bodyValue(Map.of("client_id", powensProperties.clientId()))
        .retrieve()
        .bodyToMono(PowensAccessTokenResponse.class)
        .block();
  }

  @Override
  public PowensTokenCodeResponse createTemporaryCode(String permanentAccessToken) {
    return powensWebClient
        .post()
        .uri("/auth/token/code")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + permanentAccessToken)
        .bodyValue(Map.of("client_id", powensProperties.clientId()))
        .retrieve()
        .bodyToMono(PowensTokenCodeResponse.class)
        .block();
  }
}
