package com.moneyapp.backend.banking.service;

import com.moneyapp.backend.auth.entity.AppUser;
import com.moneyapp.backend.auth.repository.AppUserRepository;
import com.moneyapp.backend.banking.dto.PowensAccessTokenResponse;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PowensAuthService {

  private final AppUserRepository appUserRepository;
  private final PowensClient powensClient;

  @Transactional
  public AppUser ensurePowensUser(String email) {
    AppUser appUser =
        appUserRepository
            .findByEmail(email)
            .orElseGet(() -> appUserRepository.save(AppUser.builder().email(email).build()));

    if (hasPowensIdentity(appUser)) {
      return appUser;
    }

    PowensAccessTokenResponse response =
        Objects.requireNonNull(powensClient.createUserAccessToken(), "Powens response is required");
    if (isBlank(response.accessToken())
        || response.user() == null
        || isBlank(response.user().id())) {
      throw new IllegalStateException("Powens user creation returned an incomplete response");
    }

    appUser.setPowensToken(response.accessToken());
    appUser.setPowensUserId(response.user().id());
    return appUserRepository.save(appUser);
  }

  private boolean hasPowensIdentity(AppUser appUser) {
    return !isBlank(appUser.getPowensToken()) && !isBlank(appUser.getPowensUserId());
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
