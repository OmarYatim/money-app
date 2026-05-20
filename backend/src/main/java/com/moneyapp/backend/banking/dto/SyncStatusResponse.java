package com.moneyapp.backend.banking.dto;

import java.time.Instant;
import java.util.List;
import lombok.Builder;

@Builder
public record SyncStatusResponse(
    Instant lastSyncedAt,
    List<ConnectionRequiringActionResponse> connectionsRequiringAction,
    boolean hasSyncError) {}
