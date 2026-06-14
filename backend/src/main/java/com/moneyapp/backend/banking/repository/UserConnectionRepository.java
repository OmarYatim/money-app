package com.moneyapp.backend.banking.repository;

import com.moneyapp.backend.banking.entity.UserConnection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserConnectionRepository extends JpaRepository<UserConnection, Long> {

  Optional<UserConnection> findByUserIdAndConnectionId(Long userId, Long connectionId);

  List<UserConnection> findByUserId(Long userId);

  List<UserConnection> findByUserIdAndStatus(Long userId, String status);

  List<UserConnection> findByUserIdAndStateIsNotNull(Long userId);

  void deleteByUserId(Long userId);
}
