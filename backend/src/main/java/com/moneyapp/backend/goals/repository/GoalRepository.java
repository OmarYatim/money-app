package com.moneyapp.backend.goals.repository;

import com.moneyapp.backend.goals.entity.Goal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalRepository extends JpaRepository<Goal, Long> {

  List<Goal> findByUserIdAndArchivedFalseOrderByTargetDateAscIdAsc(Long userId);

  Optional<Goal> findByIdAndUserId(Long id, Long userId);

  List<Goal> findByUserIdAndLinkedAccountIdInAndArchivedFalse(Long userId, List<Long> accountIds);
}
