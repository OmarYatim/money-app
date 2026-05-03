package com.moneyapp.backend.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.moneyapp.backend.transaction.enums.CategoryType;
import org.junit.jupiter.api.Test;

class CategoryMappingServiceTest {

  private final CategoryMappingService categoryMappingService = new CategoryMappingService();

  @Test
  void shouldMapFeedToGroceries() {
    assertThat(categoryMappingService.map(2)).isEqualTo(CategoryType.GROCERIES);
  }

  @Test
  void shouldMapTransportToTransport() {
    assertThat(categoryMappingService.map(11)).isEqualTo(CategoryType.TRANSPORT);
  }

  @Test
  void shouldMapInsuranceToHealth() {
    assertThat(categoryMappingService.map(3)).isEqualTo(CategoryType.HEALTH);
  }

  @Test
  void shouldReturnOtherForIndefini() {
    assertThat(categoryMappingService.map(9998)).isEqualTo(CategoryType.OTHER);
  }

  @Test
  void shouldReturnOtherForNullCategory() {
    assertThat(categoryMappingService.map(null)).isEqualTo(CategoryType.OTHER);
  }

  @Test
  void shouldReturnOtherForUnknownId() {
    assertThat(categoryMappingService.map(99999)).isEqualTo(CategoryType.OTHER);
  }
}
