package com.moneyapp.backend.transaction.repository;

import com.moneyapp.backend.transaction.entity.Transaction;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

  List<Transaction> findByUserIdOrderByDateDescIdDesc(Long userId);

  Optional<Transaction> findByUserIdAndExternalTransactionId(
      Long userId, Long externalTransactionId);
}
