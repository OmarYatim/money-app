package com.moneyapp.backend.auth.dto;

public record MfaEnrolmentResponse(String qrCodeDataUrl, String secret) {}
