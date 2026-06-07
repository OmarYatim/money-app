package com.moneyapp.backend.auth.dto;

import java.time.LocalDateTime;

public record RegisterChallengeResponse(String email, LocalDateTime expiresAt) {}
