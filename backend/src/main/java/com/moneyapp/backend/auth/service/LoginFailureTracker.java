package com.moneyapp.backend.auth.service;

import com.moneyapp.backend.config.AppProperties;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Service;

@Service
public class LoginFailureTracker {

  private final AppProperties appProperties;
  private final ConcurrentMap<String, Integer> failuresByEmail = new ConcurrentHashMap<>();

  public LoginFailureTracker(AppProperties appProperties) {
    this.appProperties = appProperties;
  }

  public void delayIfRequired(String email) {
    if (!appProperties.rateLimit().enabled()) {
      return;
    }
    int failures = failuresByEmail.getOrDefault(normalize(email), 0);
    if (failures < appProperties.rateLimit().loginFailure().threshold()) {
      return;
    }
    try {
      Thread.sleep(appProperties.rateLimit().loginFailure().delaySeconds() * 1000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  public void recordFailure(String email) {
    if (!appProperties.rateLimit().enabled()) {
      return;
    }
    failuresByEmail.merge(normalize(email), 1, Integer::sum);
  }

  public void clearFailures(String email) {
    failuresByEmail.remove(normalize(email));
  }

  private String normalize(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }
}
