package com.moneyapp.backend.banking.dto;

import java.util.List;
import lombok.Builder;

@Builder
public record SyncStatusResponse(
    List<ConnectionRequiringActionResponse> connectionsRequiringAction) {}
