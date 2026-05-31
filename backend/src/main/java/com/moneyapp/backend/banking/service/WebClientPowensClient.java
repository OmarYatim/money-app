package com.moneyapp.backend.banking.service;

import com.moneyapp.backend.banking.dto.PowensAccessTokenResponse;
import com.moneyapp.backend.banking.dto.PowensAccountsResponse;
import com.moneyapp.backend.banking.dto.PowensConnectionsResponse;
import com.moneyapp.backend.banking.dto.PowensTokenCodeResponse;
import com.moneyapp.backend.config.PowensProperties;
import com.moneyapp.backend.transaction.dto.PowensTransactionsResponse;
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
        .uri("/auth/init")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + powensProperties.manageToken())
        .bodyValue(
            Map.of(
                "client_id", powensProperties.clientId(),
                "client_secret", powensProperties.clientSecret()))
        .retrieve()
        .bodyToMono(PowensAccessTokenResponse.class)
        .block();
  }

  @Override
  public PowensTokenCodeResponse createTemporaryCode(String permanentAccessToken) {
    return powensWebClient
        .get()
        .uri("/auth/token/code")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + permanentAccessToken)
        .retrieve()
        .bodyToMono(PowensTokenCodeResponse.class)
        .block();
  }

  @Override
  public PowensAccountsResponse fetchAccounts(String permanentAccessToken) {
    return powensWebClient
        .get()
        .uri("/users/me/accounts")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + permanentAccessToken)
        .retrieve()
        .bodyToMono(PowensAccountsResponse.class)
        .block();
  }

  @Override
  public PowensConnectionsResponse fetchConnections(String permanentAccessToken) {
    return powensWebClient
        .get()
        .uri("/users/me/connections")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + permanentAccessToken)
        .retrieve()
        .bodyToMono(PowensConnectionsResponse.class)
        .block();
  }

  @Override
  public void deleteConnection(String permanentAccessToken, Long connectionId) {
    powensWebClient
        .delete()
        .uri("/users/me/connections/{connectionId}", connectionId)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + permanentAccessToken)
        .retrieve()
        .toBodilessEntity()
        .block();
  }

  @Override
  public PowensTransactionsResponse fetchTransactions(String permanentAccessToken) {
    return powensWebClient
        .get()
        .uri(
            uriBuilder ->
                uriBuilder.path("/users/me/transactions").queryParam("limit", 500).build())
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + permanentAccessToken)
        .retrieve()
        .bodyToMono(PowensTransactionsResponse.class)
        .block();
  }
}
