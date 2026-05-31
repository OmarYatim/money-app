package com.moneyapp.backend.banking.entity;

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
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "account",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_account_user_external_id",
            columnNames = {"user_id", "external_account_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "connection_id")
  private Long connectionId;

  @Column(name = "external_account_id", nullable = false)
  private Long externalAccountId;

  @Column(name = "institution_name")
  private String institutionName;

  @Column(nullable = false)
  private String name;

  @Column(name = "display_name")
  private String displayName;

  @Column(length = 100)
  private String type;

  @Column(name = "account_number_last_four", length = 4)
  private String accountNumberLastFour;

  @Column(nullable = false, precision = 19, scale = 4)
  @Builder.Default
  private BigDecimal balance = BigDecimal.ZERO;

  @Column(nullable = false, precision = 19, scale = 4)
  @Builder.Default
  private BigDecimal coming = BigDecimal.ZERO;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(name = "last_update")
  private LocalDateTime lastUpdate;

  @Column(nullable = false)
  @Builder.Default
  private boolean disabled = false;

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
