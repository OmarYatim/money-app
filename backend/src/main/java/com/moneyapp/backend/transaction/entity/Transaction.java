package com.moneyapp.backend.transaction.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "\"transaction\"",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_transaction_user_external_id",
            columnNames = {"user_id", "external_transaction_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "account_id")
  private Long accountId;

  @Column(name = "external_account_id")
  private Long externalAccountId;

  @Column(name = "external_transaction_id", nullable = false)
  private Long externalTransactionId;

  @Column(nullable = false)
  private LocalDate date;

  @Column(nullable = false)
  private String label;

  @Column private String wording;

  @Column(name = "transaction_value", nullable = false, precision = 19, scale = 4)
  @Builder.Default
  private BigDecimal value = BigDecimal.ZERO;

  @Column(length = 50)
  private String type;

  @Column(nullable = false, length = 30)
  private String category;

  @Column(name = "category_overridden", nullable = false)
  @Builder.Default
  private boolean categoryOverridden = false;

  @Column(name = "internal_transfer", nullable = false)
  @Builder.Default
  private boolean internalTransfer = false;

  @Column(name = "internal_transfer_overridden", nullable = false)
  @Builder.Default
  private boolean internalTransferOverridden = false;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @PrePersist
  protected void onCreate() {
    LocalDateTime now = LocalDateTime.now();
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = LocalDateTime.now();
  }
}
