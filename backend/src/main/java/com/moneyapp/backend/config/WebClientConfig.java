package com.moneyapp.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

  @Bean
  WebClient powensWebClient(PowensProperties powensProperties) {
    ExchangeStrategies strategies =
        ExchangeStrategies.builder()
            .codecs(c -> c.defaultCodecs().maxInMemorySize(5 * 1024 * 1024))
            .build();
    return WebClient.builder()
        .baseUrl("https://" + powensProperties.domain() + "/2.0")
        .exchangeStrategies(strategies)
        .build();
  }
}
