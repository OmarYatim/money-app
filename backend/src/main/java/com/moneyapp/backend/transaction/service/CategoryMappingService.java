package com.moneyapp.backend.transaction.service;

import com.moneyapp.backend.transaction.enums.CategoryType;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class CategoryMappingService {

  // Powens category IDs from GET /2.0/banks/categories
  // 9998 = "Indéfini" (unclassified) — the only ID returned in sandbox
  private static final Map<Integer, CategoryType> POWENS_CATEGORY_MAPPINGS =
      Map.ofEntries(
          Map.entry(2, CategoryType.GROCERIES),
          Map.entry(3, CategoryType.HEALTH),
          Map.entry(5, CategoryType.UTILITIES),
          Map.entry(6, CategoryType.SHOPPING),
          Map.entry(9, CategoryType.RENT),
          Map.entry(10, CategoryType.ENTERTAINMENT),
          Map.entry(11, CategoryType.TRANSPORT));

  public CategoryType map(Integer powensCategoryId) {
    if (powensCategoryId == null) {
      return CategoryType.OTHER;
    }

    return POWENS_CATEGORY_MAPPINGS.getOrDefault(powensCategoryId, CategoryType.OTHER);
  }
}
