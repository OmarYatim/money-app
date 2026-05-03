package com.moneyapp.backend.dashboard.controller;

import com.moneyapp.backend.dashboard.dto.DashboardSummaryResponse;
import com.moneyapp.backend.dashboard.service.DashboardSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

  private final DashboardSummaryService dashboardSummaryService;

  @GetMapping("/summary")
  public ResponseEntity<DashboardSummaryResponse> getSummary(Authentication authentication) {
    return ResponseEntity.ok(dashboardSummaryService.compute(authentication.getName()));
  }
}
