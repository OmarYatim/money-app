package com.moneyapp.backend.stream.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moneyapp.backend.auth.entity.AppUser;
import com.moneyapp.backend.auth.repository.AppUserRepository;
import com.moneyapp.backend.auth.service.CurrentAppUserService;
import com.moneyapp.backend.auth.service.JwtService;
import com.moneyapp.backend.config.AppProperties;
import com.moneyapp.backend.stream.service.SseEmitterService;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SseControllerTest {

  @Test
  void opensEmitterWhenAccessTokenIsValid() {
    AppProperties properties =
        new AppProperties(
            "http://localhost:4200",
            List.of("http://localhost:4200"),
            new AppProperties.JwtProperties("test-secret-key-must-be-at-least-32-chars!!", 900000),
            new AppProperties.MailProperties("noreply@example.com"),
            new AppProperties.AuthProperties(false),
            new AppProperties.RateLimitProperties(
                true,
                new AppProperties.EndpointRateLimitProperties(5, 900),
                new AppProperties.EndpointRateLimitProperties(20, 900),
                new AppProperties.LoginFailureProperties(5, 15)));
    JwtService jwtService = new JwtService(properties);
    AppUser appUser = AppUser.builder().id(42L).email("person@example.com").build();
    CapturingSseEmitterService sseEmitterService = new CapturingSseEmitterService();
    SseController controller =
        new SseController(
            jwtService,
            new CurrentAppUserService(appUserRepository(Optional.of(appUser))),
            sseEmitterService);

    SseEmitter emitter = controller.events(jwtService.generateToken(appUser.getEmail()));

    assertThat(emitter).isNotNull();
    assertThat(sseEmitterService.registeredUserId).isEqualTo(42L);
  }

  @Test
  void rejectsInvalidAccessToken() {
    AppProperties properties =
        new AppProperties(
            "http://localhost:4200",
            List.of("http://localhost:4200"),
            new AppProperties.JwtProperties("test-secret-key-must-be-at-least-32-chars!!", 900000),
            new AppProperties.MailProperties("noreply@example.com"),
            new AppProperties.AuthProperties(false),
            new AppProperties.RateLimitProperties(
                true,
                new AppProperties.EndpointRateLimitProperties(5, 900),
                new AppProperties.EndpointRateLimitProperties(20, 900),
                new AppProperties.LoginFailureProperties(5, 15)));
    SseController controller =
        new SseController(
            new JwtService(properties),
            new CurrentAppUserService(appUserRepository(Optional.empty())),
            new CapturingSseEmitterService());

    assertThatThrownBy(() -> controller.events("invalid-token"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("401");
  }

  private AppUserRepository appUserRepository(Optional<AppUser> appUser) {
    return (AppUserRepository)
        Proxy.newProxyInstance(
            AppUserRepository.class.getClassLoader(),
            new Class<?>[] {AppUserRepository.class},
            (proxy, method, args) -> {
              if ("findByEmail".equals(method.getName())) {
                return appUser;
              }
              throw new UnsupportedOperationException(method.getName());
            });
  }

  private static class CapturingSseEmitterService extends SseEmitterService {
    private Long registeredUserId;

    @Override
    public SseEmitter register(Long userId) {
      registeredUserId = userId;
      return new SseEmitter();
    }
  }
}
