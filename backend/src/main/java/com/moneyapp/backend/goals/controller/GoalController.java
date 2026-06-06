package com.moneyapp.backend.goals.controller;

import com.moneyapp.backend.goals.dto.CreateContributionRequest;
import com.moneyapp.backend.goals.dto.CreateGoalRequest;
import com.moneyapp.backend.goals.dto.GoalContributionResponse;
import com.moneyapp.backend.goals.dto.GoalProgressResponse;
import com.moneyapp.backend.goals.dto.UpdateGoalRequest;
import com.moneyapp.backend.goals.service.GoalService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/goals")
@RequiredArgsConstructor
public class GoalController {

  private final GoalService goalService;

  @GetMapping
  public ResponseEntity<List<GoalProgressResponse>> getGoals(Authentication authentication) {
    return ResponseEntity.ok(goalService.findGoals(authenticatedEmail(authentication)));
  }

  @GetMapping("/{id}")
  public ResponseEntity<GoalProgressResponse> getGoal(
      @PathVariable Long id, Authentication authentication) {
    return ResponseEntity.ok(goalService.getGoal(authenticatedEmail(authentication), id));
  }

  @GetMapping("/{id}/contributions")
  public ResponseEntity<List<GoalContributionResponse>> getContributions(
      @PathVariable Long id, Authentication authentication) {
    return ResponseEntity.ok(goalService.findContributions(authenticatedEmail(authentication), id));
  }

  @PostMapping
  public ResponseEntity<GoalProgressResponse> createGoal(
      @Valid @RequestBody CreateGoalRequest request, Authentication authentication) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(goalService.createGoal(authenticatedEmail(authentication), request));
  }

  @PutMapping("/{id}")
  public ResponseEntity<GoalProgressResponse> updateGoal(
      @PathVariable Long id,
      @Valid @RequestBody UpdateGoalRequest request,
      Authentication authentication) {
    return ResponseEntity.ok(
        goalService.updateGoal(authenticatedEmail(authentication), id, request));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> archiveGoal(@PathVariable Long id, Authentication authentication) {
    goalService.archiveGoal(authenticatedEmail(authentication), id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/contributions")
  public ResponseEntity<GoalProgressResponse> addContribution(
      @PathVariable Long id,
      @Valid @RequestBody CreateContributionRequest request,
      Authentication authentication) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(goalService.addContribution(authenticatedEmail(authentication), id, request));
  }

  private String authenticatedEmail(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }

    return authentication.getName();
  }
}
