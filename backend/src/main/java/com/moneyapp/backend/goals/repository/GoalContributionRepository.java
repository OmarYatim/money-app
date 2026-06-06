package com.moneyapp.backend.goals.repository;

import com.moneyapp.backend.goals.entity.GoalContribution;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalContributionRepository extends JpaRepository<GoalContribution, Long> {

  List<GoalContribution> findByGoalIdOrderByContributedAtAscIdAsc(Long goalId);
}
