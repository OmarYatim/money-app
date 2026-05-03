package com.moneyapp.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

  private final AppProperties appProperties;

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    if (!appProperties.authEnabled()) {
      return http.csrf(AbstractHttpConfigurer::disable)
          .addFilterBefore(new DevAuthBypassFilter(), AnonymousAuthenticationFilter.class)
          .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
          .build();
    }

    return http.csrf(
            csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
        .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
        .httpBasic(Customizer.withDefaults())
        .formLogin(AbstractHttpConfigurer::disable)
        .build();
  }

  // Sets a fully-authenticated token on every request so controller auth checks pass.
  // Principal "dev-bypass" is recognised by CurrentAppUserService as the fallback dev user.
  private static class DevAuthBypassFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
        HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
      if (SecurityContextHolder.getContext().getAuthentication() == null) {
        SecurityContextHolder.getContext()
            .setAuthentication(
                new UsernamePasswordAuthenticationToken("dev-bypass", null, List.of()));
      }
      chain.doFilter(request, response);
    }
  }
}
