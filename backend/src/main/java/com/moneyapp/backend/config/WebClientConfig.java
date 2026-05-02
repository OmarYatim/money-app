package com.moneyapp.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

  @Bean
  WebClient powensWebClient(PowensProperties powensProperties) {
    return WebClient.builder().baseUrl("https://" + powensProperties.domain() + "/2.0").build();
  }
}
