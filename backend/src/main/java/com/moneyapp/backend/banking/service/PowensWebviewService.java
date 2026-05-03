package com.moneyapp.backend.banking.service;

import com.moneyapp.backend.config.PowensProperties;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@RequiredArgsConstructor
public class PowensWebviewService {

  private final PowensProperties powensProperties;

  public String buildConnectUrl(String temporaryCode, String state) {
    return UriComponentsBuilder.fromUriString("https://webview.powens.com/en/connect")
        .queryParam("domain", encode(powensProperties.domain()))
        .queryParam("client_id", encode(powensProperties.clientId()))
        .queryParam("redirect_uri", encode(powensProperties.redirectUrl()))
        .queryParam("code", encode(temporaryCode))
        .queryParam("state", encode(state))
        .build(true)
        .toUriString();
  }

  private String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }
}
