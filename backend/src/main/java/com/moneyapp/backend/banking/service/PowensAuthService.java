package com.moneyapp.backend.banking.service;

import com.moneyapp.backend.auth.entity.AppUser;
import com.moneyapp.backend.auth.repository.AppUserRepository;
import com.moneyapp.backend.auth.service.CurrentAppUserService;
import com.moneyapp.backend.banking.dto.PowensAccessTokenResponse;
import com.moneyapp.backend.banking.dto.PowensTokenCodeResponse;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PowensAuthService {

  private final AppUserRepository appUserRepository;
  private final CurrentAppUserService currentAppUserService;
  private final PowensClient powensClient;

  @Transactional
  public AppUser ensurePowensUser(String email) {
    AppUser appUser = currentAppUserService.resolveForWrite(email);

    if (hasPowensIdentity(appUser)) {
      return appUser;
    }

    PowensAccessTokenResponse response =
        Objects.requireNonNull(powensClient.createUserAccessToken(), "Powens response is required");
    if (isBlank(response.accessToken()) || isBlank(response.userId())) {
      throw new IllegalStateException("Powens user creation returned an incomplete response");
    }

    appUser.setPowensToken(response.accessToken());
    appUser.setPowensUserId(response.userId());
    return appUserRepository.save(appUser);
  }

  public String createTemporaryWebviewCode(AppUser appUser) {
    if (appUser == null || isBlank(appUser.getPowensToken())) {
      throw new IllegalStateException("Powens permanent token is required");
    }

    PowensTokenCodeResponse response =
        Objects.requireNonNull(
            powensClient.createTemporaryCode(appUser.getPowensToken()),
            "Powens temporary code response is required");
    if (isBlank(response.code())) {
      throw new IllegalStateException("Powens temporary code response was incomplete");
    }

    return response.code();
  }

  private boolean hasPowensIdentity(AppUser appUser) {
    return !isBlank(appUser.getPowensToken()) && !isBlank(appUser.getPowensUserId());
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
