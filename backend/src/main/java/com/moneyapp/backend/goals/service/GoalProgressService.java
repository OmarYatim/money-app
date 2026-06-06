package com.moneyapp.backend.goals.service;

import com.moneyapp.backend.banking.entity.Account;
import com.moneyapp.backend.banking.mapper.AccountMapper;
import com.moneyapp.backend.goals.dto.GoalProgressResponse;
import com.moneyapp.backend.goals.entity.Goal;
import com.moneyapp.backend.goals.entity.GoalContribution;
import com.moneyapp.backend.goals.mapper.GoalMapper;
import com.moneyapp.backend.goals.repository.GoalContributionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GoalProgressService {

  private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
  private static final BigDecimal DAYS_PER_MONTH = BigDecimal.valueOf(30.4375);

  private final GoalContributionRepository goalContributionRepository;

  public GoalProgressResponse computeProgress(Goal goal) {
    return computeProgress(goal, null);
  }

  public GoalProgressResponse computeProgress(Goal goal, Account linkedAccount) {
    if (linkedAccount != null) {
      goal.setCurrentAmount(defaultMoney(linkedAccount.getBalance()));
    } else if (goal.getLinkedAccountId() == null) {
      goal.setCurrentAmount(manualContributionTotal(goal.getId()));
    }

    BigDecimal currentAmount = defaultMoney(goal.getCurrentAmount());
    BigDecimal targetAmount = defaultMoney(goal.getTargetAmount());
    BigDecimal progressPercent = progressPercent(currentAmount, targetAmount);
    BigDecimal monthlyRate = monthlyRate(currentAmount, goal.getCreatedAt());
    LocalDate projectedCompletionDate =
        projectedCompletionDate(currentAmount, targetAmount, monthlyRate);

    return GoalMapper.toProgressResponse(
        goal,
        progressPercent,
        monthlyRate,
        projectedCompletionDate,
        linkedAccount == null ? null : AccountMapper.displayName(linkedAccount));
  }

  private BigDecimal manualContributionTotal(Long goalId) {
    if (goalId == null) {
      return BigDecimal.ZERO;
    }

    return goalContributionRepository.findByGoalIdOrderByContributedAtAscIdAsc(goalId).stream()
        .map(GoalContribution::getAmount)
        .map(this::defaultMoney)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private BigDecimal progressPercent(BigDecimal currentAmount, BigDecimal targetAmount) {
    if (targetAmount.compareTo(BigDecimal.ZERO) <= 0) {
      return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
    }

    return currentAmount
        .multiply(ONE_HUNDRED)
        .divide(targetAmount, 1, RoundingMode.HALF_UP)
        .min(ONE_HUNDRED);
  }

  private BigDecimal monthlyRate(BigDecimal currentAmount, LocalDateTime createdAt) {
    if (currentAmount.compareTo(BigDecimal.ZERO) <= 0 || createdAt == null) {
      return BigDecimal.ZERO;
    }

    long months = ChronoUnit.MONTHS.between(createdAt.toLocalDate(), LocalDate.now());
    long normalizedMonths = Math.max(1L, months);
    return currentAmount.divide(BigDecimal.valueOf(normalizedMonths), 2, RoundingMode.HALF_UP);
  }

  private LocalDate projectedCompletionDate(
      BigDecimal currentAmount, BigDecimal targetAmount, BigDecimal monthlyRate) {
    if (monthlyRate.compareTo(BigDecimal.ZERO) <= 0 || currentAmount.compareTo(targetAmount) >= 0) {
      return null;
    }

    BigDecimal remaining = targetAmount.subtract(currentAmount);
    BigDecimal monthsRemaining = remaining.divide(monthlyRate, 4, RoundingMode.CEILING);
    long daysRemaining =
        monthsRemaining.multiply(DAYS_PER_MONTH).setScale(0, RoundingMode.CEILING).longValue();
    return LocalDate.now().plusDays(daysRemaining);
  }

  private BigDecimal defaultMoney(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }
}
