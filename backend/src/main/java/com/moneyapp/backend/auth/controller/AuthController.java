package com.moneyapp.backend.auth.controller;

import com.moneyapp.backend.auth.dto.LoginRequest;
import com.moneyapp.backend.auth.dto.LoginResponse;
import com.moneyapp.backend.auth.dto.RegisterRequest;
import com.moneyapp.backend.auth.service.AuthenticationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthenticationService authenticationService;

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public LoginResponse register(
      @RequestBody RegisterRequest request, HttpServletResponse response) {
    return authenticationService.register(request, response);
  }

  @PostMapping("/login")
  public LoginResponse login(@RequestBody LoginRequest request, HttpServletResponse response) {
    return authenticationService.login(request, response);
  }

  @PostMapping("/refresh")
  public LoginResponse refresh(HttpServletRequest request, HttpServletResponse response) {
    return authenticationService.refresh(request, response);
  }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(HttpServletRequest request, HttpServletResponse response) {
    authenticationService.logout(request, response);
  }
}
