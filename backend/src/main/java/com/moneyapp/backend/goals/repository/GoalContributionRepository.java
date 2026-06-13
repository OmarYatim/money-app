package com.moneyapp.backend.goals.repository;

import com.moneyapp.backend.goals.entity.GoalContribution;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GoalContributionRepository extends JpaRepository<GoalContribution, Long> {

  List<GoalContribution> findByGoalIdOrderByContributedAtAscIdAsc(Long goalId);

  @Modifying
  @Query(
      """
      delete from GoalContribution contribution
      where contribution.goalId in (
        select goal.id
        from Goal goal
        where goal.userId = :userId
      )
      """)
  void deleteByGoalUserId(@Param("userId") Long userId);
}
