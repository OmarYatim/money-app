package com.moneyapp.backend.banking.repository;

import com.moneyapp.backend.banking.entity.BankConnectionState;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankConnectionStateRepository extends JpaRepository<BankConnectionState, Long> {

  Optional<BankConnectionState> findByUserIdAndStateAndConsumedFalse(Long userId, String state);
}
