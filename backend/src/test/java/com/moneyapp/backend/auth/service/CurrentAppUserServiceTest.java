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

@SpringBootTest(
    properties = {
      "powens.domain=powens.test",
      "powens.client-id=test-client-id",
      "powens.client-secret=test-client-secret",
      "powens.manage-token=test-manage-token",
      "powens.redirect-url=https://local.nexioo.me/api/bank/callback",
      "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!",
      "app.jwt.expiration-ms=900000"
    })
@ActiveProfiles("test")
class CurrentAppUserServiceTest {

  @Autowired private AppUserRepository appUserRepository;

  @Autowired private CurrentAppUserService currentAppUserService;

  @BeforeEach
  void setUp() {
    appUserRepository.deleteAll();
  }

  @Test
  void resolveExistingReturnsUserByEmail() {
    AppUser saved = appUserRepository.save(AppUser.builder().email("user@example.com").build());

    AppUser result = currentAppUserService.resolveExisting("user@example.com");

    assertThat(result.getId()).isEqualTo(saved.getId());
  }

  @Test
  void resolveExistingThrows401WhenEmailNotFound() {
    assertThatThrownBy(() -> currentAppUserService.resolveExisting("nobody@example.com"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("401 UNAUTHORIZED");
  }

  @Test
  void resolveForWriteReturnsExistingUser() {
    AppUser saved = appUserRepository.save(AppUser.builder().email("user@example.com").build());

    AppUser result = currentAppUserService.resolveForWrite("user@example.com");

    assertThat(result.getId()).isEqualTo(saved.getId());
  }

  @Test
  void resolveForWriteCreatesUserWhenNotFound() {
    AppUser result = currentAppUserService.resolveForWrite("new@example.com");

    assertThat(result.getId()).isNotNull();
    assertThat(result.getEmail()).isEqualTo("new@example.com");
  }
}
