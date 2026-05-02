package com.moneyapp.backend.banking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moneyapp.backend.auth.entity.AppUser;
import com.moneyapp.backend.auth.repository.AppUserRepository;
import com.moneyapp.backend.banking.dto.PowensAccessTokenResponse;
import com.moneyapp.backend.banking.dto.PowensUserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
    properties = {
      "powens.domain=powens.test",
      "powens.client-id=test-client-id",
      "powens.client-secret=test-client-secret",
      "powens.manage-token=test-manage-token",
      "powens.redirect-url=https://local.moneyapp.me/api/bank/callback"
    })
@ActiveProfiles("test")
class PowensAuthServiceTest {

  @Autowired private AppUserRepository appUserRepository;

  @Test
  void ensurePowensUserCreatesAndPersistsPowensIdentity() {
    StubPowensClient powensClient =
        new StubPowensClient(
            new PowensAccessTokenResponse("permanent-token", new PowensUserResponse("powens-123")));
    PowensAuthService service = new PowensAuthService(appUserRepository, powensClient);

    AppUser appUser = service.ensurePowensUser("person@example.com");

    assertThat(appUser.getPowensToken()).isEqualTo("permanent-token");
    assertThat(appUser.getPowensUserId()).isEqualTo("powens-123");
    assertThat(powensClient.calls).isEqualTo(1);
  }

  @Test
  void ensurePowensUserReusesExistingPowensIdentity() {
    appUserRepository.save(
        AppUser.builder()
            .email("existing@example.com")
            .powensToken("existing-token")
            .powensUserId("existing-powens-id")
            .build());
    StubPowensClient powensClient =
        new StubPowensClient(
            new PowensAccessTokenResponse("new-token", new PowensUserResponse("new-id")));
    PowensAuthService service = new PowensAuthService(appUserRepository, powensClient);

    AppUser appUser = service.ensurePowensUser("existing@example.com");

    assertThat(appUser.getPowensToken()).isEqualTo("existing-token");
    assertThat(appUser.getPowensUserId()).isEqualTo("existing-powens-id");
    assertThat(powensClient.calls).isZero();
  }

  @Test
  void ensurePowensUserRejectsIncompletePowensResponse() {
    PowensAuthService service =
        new PowensAuthService(
            appUserRepository, new StubPowensClient(new PowensAccessTokenResponse(null, null)));

    assertThatThrownBy(() -> service.ensurePowensUser("bad-response@example.com"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Powens user creation returned an incomplete response");
  }

  private static class StubPowensClient implements PowensClient {

    private final PowensAccessTokenResponse response;
    private int calls;

    private StubPowensClient(PowensAccessTokenResponse response) {
      this.response = response;
    }

    @Override
    public PowensAccessTokenResponse createUserAccessToken() {
      calls++;
      return response;
    }
  }
}
