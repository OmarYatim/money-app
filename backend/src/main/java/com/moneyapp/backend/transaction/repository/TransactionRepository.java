package com.moneyapp.backend.transaction.repository;

import com.moneyapp.backend.transaction.entity.Transaction;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface TransactionRepository
    extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {

  List<Transaction> findByUserIdOrderByDateDescIdDesc(Long userId);

  List<Transaction> findByUserIdAndDate(Long userId, LocalDate date);

  List<Transaction> findByUserIdAndDateBetween(Long userId, LocalDate startDate, LocalDate endDate);

  List<Transaction> findByUserIdAndTypeAndInternalTransferOverriddenFalse(Long userId, String type);

  Optional<Transaction> findByUserIdAndExternalTransactionId(
      Long userId, Long externalTransactionId);

  @Transactional
  void deleteByUserIdAndAccountIdIn(Long userId, List<Long> accountIds);

  @Transactional
  void deleteByUserId(Long userId);

  @Query(
      """
      select distinct t.accountId
      from Transaction t
      where t.userId = :userId and t.accountId is not null
      """)
  List<Long> findDistinctAccountIdsWithTransactions(@Param("userId") Long userId);
}
