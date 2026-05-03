package com.moneyapp.backend.banking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moneyapp.backend.auth.entity.AppUser;
import com.moneyapp.backend.auth.repository.AppUserRepository;
import com.moneyapp.backend.auth.service.CurrentAppUserService;
import com.moneyapp.backend.banking.dto.PowensAccessTokenResponse;
import com.moneyapp.backend.banking.dto.PowensAccountsResponse;
import com.moneyapp.backend.banking.dto.PowensConnectionsResponse;
import com.moneyapp.backend.banking.dto.PowensTokenCodeResponse;
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
      "powens.redirect-url=https://local.nexioo.me/api/bank/callback"
    })
@ActiveProfiles("test")
class PowensAuthServiceTest {

  @Autowired private AppUserRepository appUserRepository;

  @Autowired private CurrentAppUserService currentAppUserService;

  @Test
  void ensurePowensUserCreatesAndPersistsPowensIdentity() {
    StubPowensClient powensClient =
        StubPowensClient.withAccessToken(
            new PowensAccessTokenResponse("permanent-token", "powens-123"));
    PowensAuthService service =
        new PowensAuthService(appUserRepository, currentAppUserService, powensClient);

    AppUser appUser = service.ensurePowensUser("auth-person@example.com");

    assertThat(appUser.getPowensToken()).isEqualTo("permanent-token");
    assertThat(appUser.getPowensUserId()).isEqualTo("powens-123");
    assertThat(powensClient.accessTokenCalls).isEqualTo(1);
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
        StubPowensClient.withAccessToken(new PowensAccessTokenResponse("new-token", "new-id"));
    PowensAuthService service =
        new PowensAuthService(appUserRepository, currentAppUserService, powensClient);

    AppUser appUser = service.ensurePowensUser("existing@example.com");

    assertThat(appUser.getPowensToken()).isEqualTo("existing-token");
    assertThat(appUser.getPowensUserId()).isEqualTo("existing-powens-id");
    assertThat(powensClient.accessTokenCalls).isZero();
  }

  @Test
  void ensurePowensUserRejectsIncompletePowensResponse() {
    PowensAuthService service =
        new PowensAuthService(
            appUserRepository,
            currentAppUserService,
            StubPowensClient.withAccessToken(new PowensAccessTokenResponse(null, null)));

    assertThatThrownBy(() -> service.ensurePowensUser("bad-response@example.com"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Powens user creation returned an incomplete response");
  }

  @Test
  void createTemporaryWebviewCodeReturnsShortLivedCode() {
    StubPowensClient powensClient =
        StubPowensClient.withTemporaryCode(new PowensTokenCodeResponse("short-lived-code"));
    PowensAuthService service =
        new PowensAuthService(appUserRepository, currentAppUserService, powensClient);

    String code =
        service.createTemporaryWebviewCode(
            AppUser.builder().email("person@example.com").powensToken("permanent-token").build());

    assertThat(code).isEqualTo("short-lived-code");
    assertThat(powensClient.temporaryCodeCalls).isEqualTo(1);
    assertThat(powensClient.lastPermanentAccessToken).isEqualTo("permanent-token");
  }

  @Test
  void createTemporaryWebviewCodeRejectsMissingPermanentToken() {
    PowensAuthService service =
        new PowensAuthService(
            appUserRepository,
            currentAppUserService,
            StubPowensClient.withTemporaryCode(new PowensTokenCodeResponse("short-lived-code")));

    assertThatThrownBy(
            () ->
                service.createTemporaryWebviewCode(
                    AppUser.builder().email("person@example.com").build()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Powens permanent token is required");
  }

  @Test
  void createTemporaryWebviewCodeRejectsIncompletePowensResponse() {
    PowensAuthService service =
        new PowensAuthService(
            appUserRepository,
            currentAppUserService,
            StubPowensClient.withTemporaryCode(new PowensTokenCodeResponse(" ")));

    assertThatThrownBy(
            () ->
                service.createTemporaryWebviewCode(
                    AppUser.builder().email("person@example.com").powensToken("token").build()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Powens temporary code response was incomplete");
  }

  private static class StubPowensClient implements PowensClient {

    private PowensAccessTokenResponse accessTokenResponse;
    private PowensTokenCodeResponse temporaryCodeResponse;
    private int accessTokenCalls;
    private int temporaryCodeCalls;
    private String lastPermanentAccessToken;

    private static StubPowensClient withAccessToken(PowensAccessTokenResponse response) {
      StubPowensClient client = new StubPowensClient();
      client.accessTokenResponse = response;
      return client;
    }

    private static StubPowensClient withTemporaryCode(PowensTokenCodeResponse response) {
      StubPowensClient client = new StubPowensClient();
      client.temporaryCodeResponse = response;
      return client;
    }

    @Override
    public PowensAccessTokenResponse createUserAccessToken() {
      accessTokenCalls++;
      return accessTokenResponse;
    }

    @Override
    public PowensTokenCodeResponse createTemporaryCode(String permanentAccessToken) {
      temporaryCodeCalls++;
      lastPermanentAccessToken = permanentAccessToken;
      return temporaryCodeResponse;
    }

    @Override
    public PowensAccountsResponse fetchAccounts(String permanentAccessToken) {
      throw new UnsupportedOperationException("Not needed in this test");
    }

    @Override
    public PowensConnectionsResponse fetchConnections(String permanentAccessToken) {
      throw new UnsupportedOperationException("Not needed in this test");
    }
  }
}
