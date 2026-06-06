package com.moneyapp.backend.goals.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "goal")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Goal {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "linked_account_id")
  private Long linkedAccountId;

  @Column(nullable = false)
  private String name;

  @Column(name = "target_amount", nullable = false, precision = 19, scale = 4)
  private BigDecimal targetAmount;

  @Column(name = "target_date")
  private LocalDate targetDate;

  @Column(nullable = false, length = 50)
  @Builder.Default
  private String icon = "flag";

  @Column(nullable = false, length = 30)
  @Builder.Default
  private String color = "indigo";

  @Column(nullable = false, length = 100)
  @Builder.Default
  private String category = "Other";

  @Column(nullable = false, length = 30)
  @Builder.Default
  private String priority = "Medium";

  @Column(length = 1000)
  private String note;

  @Column(name = "auto_save_enabled", nullable = false)
  @Builder.Default
  private boolean autoSaveEnabled = false;

  @Column(name = "planned_monthly_contribution", nullable = false, precision = 19, scale = 4)
  @Builder.Default
  private BigDecimal plannedMonthlyContribution = BigDecimal.ZERO;

  @Column(name = "current_amount", nullable = false, precision = 19, scale = 4)
  @Builder.Default
  private BigDecimal currentAmount = BigDecimal.ZERO;

  @Column(nullable = false)
  @Builder.Default
  private boolean archived = false;

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
