package com.moneyapp.backend.reports.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record NetWorthHistoryResponse(LocalDate date, BigDecimal netWorth) {}
