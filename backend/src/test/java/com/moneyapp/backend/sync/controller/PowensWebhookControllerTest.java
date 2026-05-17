package com.moneyapp.backend.sync.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.moneyapp.backend.auth.entity.AppUser;
import com.moneyapp.backend.auth.repository.AppUserRepository;
import com.moneyapp.backend.sync.enums.SyncEventTrigger;
import com.moneyapp.backend.sync.service.AsyncDataSyncService;
import java.lang.reflect.Proxy;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.json.JsonMapper;

class PowensWebhookControllerTest {

  @Test
  void syncsWhenUserScopedPowensTokenMatches() {
    AppUser appUser =
        AppUser.builder().id(1L).powensUserId("23").powensToken("user-powens-token").build();
    CapturingAsyncDataSyncService asyncDataSyncService = new CapturingAsyncDataSyncService();
    PowensWebhookController controller =
        controller(appUserRepository(Optional.of(appUser)), asyncDataSyncService);

    ResponseEntity<Void> response =
        controller.handleWebhook(
            "Bearer user-powens-token",
            "{\"connection\":{\"id\":47,\"id_user\":23},\"user\":{\"id\":23}}");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(asyncDataSyncService.appUser).isSameAs(appUser);
    assertThat(asyncDataSyncService.triggeredBy).isEqualTo(SyncEventTrigger.WEBHOOK);
    assertThat(asyncDataSyncService.connectionId).isEqualTo(47L);
  }

  @Test
  void rejectsWhenUserScopedPowensTokenDoesNotMatch() {
    AppUser appUser =
        AppUser.builder().id(1L).powensUserId("23").powensToken("user-powens-token").build();
    CapturingAsyncDataSyncService asyncDataSyncService = new CapturingAsyncDataSyncService();
    PowensWebhookController controller =
        controller(appUserRepository(Optional.of(appUser)), asyncDataSyncService);

    ResponseEntity<Void> response =
        controller.handleWebhook(
            "Bearer wrong-token",
            "{\"connection\":{\"id\":47,\"id_user\":23},\"user\":{\"id\":23}}");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(asyncDataSyncService.appUser).isNull();
  }

  @Test
  void rejectsWhenPowensUserCannotBeResolved() {
    CapturingAsyncDataSyncService asyncDataSyncService = new CapturingAsyncDataSyncService();
    PowensWebhookController controller =
        controller(appUserRepository(Optional.empty()), asyncDataSyncService);

    ResponseEntity<Void> response =
        controller.handleWebhook(
            "Bearer user-powens-token",
            "{\"connection\":{\"id\":47,\"id_user\":23},\"user\":{\"id\":23}}");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(asyncDataSyncService.appUser).isNull();
  }

  private PowensWebhookController controller(
      AppUserRepository appUserRepository, AsyncDataSyncService asyncDataSyncService) {
    return new PowensWebhookController(
        appUserRepository, asyncDataSyncService, JsonMapper.shared());
  }

  private AppUserRepository appUserRepository(Optional<AppUser> appUser) {
    return (AppUserRepository)
        Proxy.newProxyInstance(
            AppUserRepository.class.getClassLoader(),
            new Class<?>[] {AppUserRepository.class},
            (proxy, method, args) -> {
              if ("findByPowensUserId".equals(method.getName())) {
                return appUser;
              }
              throw new UnsupportedOperationException(method.getName());
            });
  }

  private static class CapturingAsyncDataSyncService extends AsyncDataSyncService {

    private AppUser appUser;
    private SyncEventTrigger triggeredBy;
    private Long connectionId;

    private CapturingAsyncDataSyncService() {
      super(null);
    }

    @Override
    public void syncAsync(AppUser appUser, SyncEventTrigger triggeredBy, Long connectionId) {
      this.appUser = appUser;
      this.triggeredBy = triggeredBy;
      this.connectionId = connectionId;
    }
  }
}
