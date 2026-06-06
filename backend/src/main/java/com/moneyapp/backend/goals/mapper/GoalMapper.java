package com.moneyapp.backend.goals.mapper;

import com.moneyapp.backend.goals.dto.GoalContributionResponse;
import com.moneyapp.backend.goals.dto.GoalProgressResponse;
import com.moneyapp.backend.goals.entity.Goal;
import com.moneyapp.backend.goals.entity.GoalContribution;
import java.math.BigDecimal;
import java.time.LocalDate;

public final class GoalMapper {

  private GoalMapper() {}

  public static GoalProgressResponse toProgressResponse(
      Goal goal,
      BigDecimal progressPercent,
      BigDecimal monthlyRate,
      LocalDate projectedCompletionDate,
      String linkedAccountName) {
    return new GoalProgressResponse(
        goal.getId(),
        goal.getName(),
        goal.getTargetAmount(),
        goal.getTargetDate(),
        goal.getIcon(),
        goal.getColor(),
        goal.getCategory(),
        goal.getPriority(),
        goal.getNote(),
        goal.isAutoSaveEnabled(),
        goal.getPlannedMonthlyContribution(),
        goal.getCurrentAmount(),
        progressPercent,
        monthlyRate,
        projectedCompletionDate,
        goal.isArchived(),
        goal.getLinkedAccountId(),
        linkedAccountName,
        goal.getCreatedAt(),
        goal.getUpdatedAt());
  }

  public static GoalContributionResponse toContributionResponse(GoalContribution contribution) {
    return new GoalContributionResponse(
        contribution.getId(),
        contribution.getGoalId(),
        contribution.getAmount(),
        contribution.getNote(),
        contribution.getContributedAt());
  }
}
