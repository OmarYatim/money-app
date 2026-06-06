package com.moneyapp.backend.goals.service;

import com.moneyapp.backend.auth.service.CurrentAppUserService;
import com.moneyapp.backend.banking.entity.Account;
import com.moneyapp.backend.banking.repository.AccountRepository;
import com.moneyapp.backend.goals.dto.CreateContributionRequest;
import com.moneyapp.backend.goals.dto.CreateGoalRequest;
import com.moneyapp.backend.goals.dto.GoalContributionResponse;
import com.moneyapp.backend.goals.dto.GoalProgressResponse;
import com.moneyapp.backend.goals.dto.UpdateGoalRequest;
import com.moneyapp.backend.goals.entity.Goal;
import com.moneyapp.backend.goals.entity.GoalContribution;
import com.moneyapp.backend.goals.mapper.GoalMapper;
import com.moneyapp.backend.goals.repository.GoalContributionRepository;
import com.moneyapp.backend.goals.repository.GoalRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class GoalService {

  private final GoalRepository goalRepository;
  private final GoalContributionRepository goalContributionRepository;
  private final AccountRepository accountRepository;
  private final CurrentAppUserService currentAppUserService;
  private final GoalProgressService goalProgressService;

  @Transactional(readOnly = true)
  public List<GoalProgressResponse> findGoals(String email) {
    Long userId = requireUserId(email);
    List<Goal> goals = goalRepository.findByUserIdAndArchivedFalseOrderByTargetDateAscIdAsc(userId);
    Map<Long, Account> accountsById = linkedAccounts(userId, goals);

    return goals.stream()
        .sorted(
            Comparator.comparing(
                Goal::getTargetDate, Comparator.nullsLast(Comparator.naturalOrder())))
        .map(goal -> goalProgressService.computeProgress(goal, accountForGoal(accountsById, goal)))
        .toList();
  }

  @Transactional(readOnly = true)
  public GoalProgressResponse getGoal(String email, Long goalId) {
    Long userId = requireUserId(email);
    Goal goal = requireOwnedGoal(goalId, userId);
    Account linkedAccount = linkedAccount(userId, goal.getLinkedAccountId());
    return goalProgressService.computeProgress(goal, linkedAccount);
  }

  @Transactional(readOnly = true)
  public List<GoalContributionResponse> findContributions(String email, Long goalId) {
    Long userId = requireUserId(email);
    requireOwnedGoal(goalId, userId);
    return goalContributionRepository.findByGoalIdOrderByContributedAtAscIdAsc(goalId).stream()
        .map(GoalMapper::toContributionResponse)
        .toList();
  }

  @Transactional
  public GoalProgressResponse createGoal(String email, CreateGoalRequest request) {
    Long userId = requireUserId(email);
    Account linkedAccount = linkedAccount(userId, request.linkedAccountId());
    Goal goal =
        Goal.builder()
            .userId(userId)
            .name(request.name().trim())
            .targetAmount(request.targetAmount())
            .targetDate(request.targetDate())
            .linkedAccountId(request.linkedAccountId())
            .icon(defaultText(request.icon(), "flag"))
            .color(defaultText(request.color(), "indigo"))
            .category(defaultText(request.category(), "Other"))
            .priority(defaultText(request.priority(), "Medium"))
            .note(normalizeNote(request.note()))
            .autoSaveEnabled(request.autoSaveEnabled())
            .plannedMonthlyContribution(defaultMoney(request.plannedMonthlyContribution()))
            .currentAmount(linkedAccount == null ? BigDecimal.ZERO : linkedAccount.getBalance())
            .archived(false)
            .build();
    Goal savedGoal = goalRepository.save(goal);
    return goalProgressService.computeProgress(savedGoal, linkedAccount);
  }

  @Transactional
  public GoalProgressResponse updateGoal(String email, Long goalId, UpdateGoalRequest request) {
    Long userId = requireUserId(email);
    Goal goal = requireOwnedGoal(goalId, userId);
    Account linkedAccount = linkedAccount(userId, request.linkedAccountId());
    goal.setName(request.name().trim());
    goal.setTargetAmount(request.targetAmount());
    goal.setTargetDate(request.targetDate());
    goal.setLinkedAccountId(request.linkedAccountId());
    goal.setIcon(defaultText(request.icon(), "flag"));
    goal.setColor(defaultText(request.color(), "indigo"));
    goal.setCategory(defaultText(request.category(), "Other"));
    goal.setPriority(defaultText(request.priority(), "Medium"));
    goal.setNote(normalizeNote(request.note()));
    goal.setAutoSaveEnabled(request.autoSaveEnabled());
    goal.setPlannedMonthlyContribution(defaultMoney(request.plannedMonthlyContribution()));
    goal.setCurrentAmount(
        linkedAccount == null ? contributionTotal(goal.getId()) : linkedAccount.getBalance());
    return goalProgressService.computeProgress(goalRepository.save(goal), linkedAccount);
  }

  @Transactional
  public void archiveGoal(String email, Long goalId) {
    Long userId = requireUserId(email);
    Goal goal = requireOwnedGoal(goalId, userId);
    goal.setArchived(true);
    goalRepository.save(goal);
  }

  @Transactional
  public GoalProgressResponse addContribution(
      String email, Long goalId, CreateContributionRequest request) {
    Long userId = requireUserId(email);
    Goal goal = requireOwnedGoal(goalId, userId);
    goalContributionRepository.save(
        GoalContribution.builder()
            .goalId(goal.getId())
            .amount(request.amount())
            .note(normalizeNote(request.note()))
            .contributedAt(
                request.contributedAt() == null ? LocalDate.now() : request.contributedAt())
            .build());
    Account linkedAccount = linkedAccount(userId, goal.getLinkedAccountId());
    goal.setCurrentAmount(
        linkedAccount == null ? contributionTotal(goal.getId()) : linkedAccount.getBalance());
    return goalProgressService.computeProgress(goalRepository.save(goal), linkedAccount);
  }

  @Transactional
  public void refreshLinkedAccountGoals(Long userId, List<Account> accounts) {
    if (accounts == null || accounts.isEmpty()) {
      return;
    }

    Map<Long, Account> accountsById =
        accounts.stream()
            .filter(account -> account.getId() != null)
            .collect(Collectors.toMap(Account::getId, Function.identity()));
    if (accountsById.isEmpty()) {
      return;
    }

    List<Goal> goals =
        goalRepository.findByUserIdAndLinkedAccountIdInAndArchivedFalse(
            userId, accountsById.keySet().stream().toList());
    goals.forEach(
        goal ->
            goal.setCurrentAmount(
                defaultMoney(accountsById.get(goal.getLinkedAccountId()).getBalance())));
    goalRepository.saveAll(goals);
  }

  private Long requireUserId(String email) {
    return currentAppUserService.resolveExisting(email).getId();
  }

  private Goal requireOwnedGoal(Long goalId, Long userId) {
    Goal goal =
        goalRepository
            .findById(goalId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Goal not found"));
    if (!goal.getUserId().equals(userId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
    }
    return goal;
  }

  private Account linkedAccount(Long userId, Long linkedAccountId) {
    if (linkedAccountId == null) {
      return null;
    }

    return accountRepository
        .findByIdAndUserId(linkedAccountId, userId)
        .orElseThrow(
            () ->
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "linkedAccountId is invalid"));
  }

  private Map<Long, Account> linkedAccounts(Long userId, List<Goal> goals) {
    List<Long> linkedAccountIds =
        goals.stream().map(Goal::getLinkedAccountId).filter(id -> id != null).distinct().toList();
    if (linkedAccountIds.isEmpty()) {
      return Map.of();
    }

    return accountRepository.findByUserIdAndIdIn(userId, linkedAccountIds).stream()
        .collect(Collectors.toMap(Account::getId, Function.identity()));
  }

  private Account accountForGoal(Map<Long, Account> accountsById, Goal goal) {
    Long linkedAccountId = goal.getLinkedAccountId();
    return linkedAccountId == null ? null : accountsById.get(linkedAccountId);
  }

  private BigDecimal contributionTotal(Long goalId) {
    return goalContributionRepository.findByGoalIdOrderByContributedAtAscIdAsc(goalId).stream()
        .map(GoalContribution::getAmount)
        .map(this::defaultMoney)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private String normalizeNote(String note) {
    if (note == null || note.isBlank()) {
      return null;
    }

    return note.trim();
  }

  private String defaultText(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  private BigDecimal defaultMoney(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }
}
