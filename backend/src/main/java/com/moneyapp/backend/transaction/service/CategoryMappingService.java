package com.moneyapp.backend.transaction.service;

import com.moneyapp.backend.transaction.enums.CategoryType;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class CategoryMappingService {

  private static final Map<String, CategoryType> POWENS_CATEGORY_MAPPINGS =
      Map.ofEntries(
          Map.entry("SUPERMARKETS", CategoryType.GROCERIES),
          Map.entry("GROCERIES", CategoryType.GROCERIES),
          Map.entry("FOOD SHOPS", CategoryType.GROCERIES),
          Map.entry("RESTAURANTS", CategoryType.DINING),
          Map.entry("BARS AND RESTAURANTS", CategoryType.DINING),
          Map.entry("PUBLIC TRANSPORT", CategoryType.TRANSPORT),
          Map.entry("TRANSPORT", CategoryType.TRANSPORT),
          Map.entry("UTILITIES", CategoryType.UTILITIES),
          Map.entry("RENT", CategoryType.RENT),
          Map.entry("HEALTH", CategoryType.HEALTH),
          Map.entry("ENTERTAINMENT", CategoryType.ENTERTAINMENT),
          Map.entry("SHOPPING", CategoryType.SHOPPING),
          Map.entry("TRAVEL", CategoryType.TRAVEL),
          Map.entry("EDUCATION", CategoryType.EDUCATION),
          Map.entry("INCOME", CategoryType.INCOME),
          Map.entry("TRANSFERS", CategoryType.TRANSFER),
          Map.entry("TRANSFER", CategoryType.TRANSFER),
          Map.entry("SAVINGS", CategoryType.SAVINGS),
          Map.entry("SUBSCRIPTIONS", CategoryType.SUBSCRIPTION),
          Map.entry("SUBSCRIPTION", CategoryType.SUBSCRIPTION));

  public CategoryType map(String powensCategory) {
    if (powensCategory == null || powensCategory.isBlank()) {
      return CategoryType.OTHER;
    }

    String normalizedCategory = powensCategory.trim().toUpperCase(Locale.ROOT);
    return POWENS_CATEGORY_MAPPINGS.getOrDefault(normalizedCategory, CategoryType.OTHER);
  }
}
