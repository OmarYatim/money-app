package com.moneyapp.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moneyapp.backend.auth.entity.AppUser;
import com.moneyapp.backend.auth.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(properties = {"app.auth-enabled=true"})
@ActiveProfiles("test")
class CurrentAppUserServiceTest {

  @Autowired private AppUserRepository appUserRepository;

  @Autowired private CurrentAppUserService currentAppUserService;

  @BeforeEach
  void setUp() {
    appUserRepository.deleteAll();
  }

  @Test
  void resolveExistingRejectsPrincipalMismatchWhenAuthIsEnabled() {
    appUserRepository.save(AppUser.builder().email("dev@nexioo.me").build());

    assertThatThrownBy(() -> currentAppUserService.resolveExisting("user"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("401 UNAUTHORIZED");
  }

  @Test
  void resolveExistingReturnsExactMatchWhenAuthIsEnabled() {
    AppUser savedUser = appUserRepository.save(AppUser.builder().email("user").build());

    AppUser appUser = currentAppUserService.resolveExisting("user");

    assertThat(appUser.getId()).isEqualTo(savedUser.getId());
  }
}
