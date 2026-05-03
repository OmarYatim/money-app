package com.moneyapp.backend.auth.service;

import com.moneyapp.backend.auth.entity.AppUser;
import com.moneyapp.backend.auth.repository.AppUserRepository;
import com.moneyapp.backend.config.AppProperties;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class CurrentAppUserService {

  private final AppProperties appProperties;
  private final AppUserRepository appUserRepository;

  @Transactional(readOnly = true)
  public AppUser resolveExisting(String principalName) {
    AppUser exactMatch = appUserRepository.findByEmail(principalName).orElse(null);
    if (exactMatch != null) {
      return exactMatch;
    }

    if (!appProperties.authEnabled()) {
      return soleUserIfPresent()
          .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
  }

  @Transactional
  public AppUser resolveForWrite(String principalName) {
    AppUser exactMatch = appUserRepository.findByEmail(principalName).orElse(null);
    if (exactMatch != null) {
      return exactMatch;
    }

    if (!appProperties.authEnabled()) {
      Optional<AppUser> soleUser = soleUserIfPresent();
      if (soleUser.isPresent()) {
        return soleUser.get();
      }
    }

    return appUserRepository.save(AppUser.builder().email(principalName).build());
  }

  private Optional<AppUser> soleUserIfPresent() {
    List<AppUser> users = appUserRepository.findAll();
    if (users.size() == 1) {
      return Optional.of(users.get(0));
    }

    return Optional.empty();
  }
}
