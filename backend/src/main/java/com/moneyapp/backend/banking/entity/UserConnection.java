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
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "user_connection",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_user_connection_user_connection_id",
            columnNames = {"user_id", "connection_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserConnection {

  public static final String STATUS_ACTIVE = "active";
  public static final String STATUS_REQUIRING_ACTION = "requiring_action";
  public static final String STATUS_SYNC_FAILED = "SYNC_FAILED";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "connection_id", nullable = false)
  private Long connectionId;

  @Column(nullable = false, length = 50)
  @Builder.Default
  private String status = STATUS_ACTIVE;

  @Column(length = 100)
  private String state;

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
