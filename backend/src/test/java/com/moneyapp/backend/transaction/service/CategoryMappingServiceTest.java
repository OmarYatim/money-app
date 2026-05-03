package com.moneyapp.backend.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.moneyapp.backend.transaction.enums.CategoryType;
import org.junit.jupiter.api.Test;

class CategoryMappingServiceTest {

  private final CategoryMappingService categoryMappingService = new CategoryMappingService();

  @Test
  void shouldMapSupermarketsToGroceries() {
    assertThat(categoryMappingService.map("Supermarkets")).isEqualTo(CategoryType.GROCERIES);
  }

  @Test
  void shouldMapRestaurantsToDining() {
    assertThat(categoryMappingService.map("Restaurants")).isEqualTo(CategoryType.DINING);
  }

  @Test
  void shouldMapPublicTransportToTransport() {
    assertThat(categoryMappingService.map("Public Transport")).isEqualTo(CategoryType.TRANSPORT);
  }

  @Test
  void shouldReturnOtherForEmptyCategory() {
    assertThat(categoryMappingService.map("")).isEqualTo(CategoryType.OTHER);
  }

  @Test
  void shouldReturnOtherForNullCategory() {
    assertThat(categoryMappingService.map(null)).isEqualTo(CategoryType.OTHER);
  }

  @Test
  void shouldReturnOtherForUnknownCategory() {
    assertThat(categoryMappingService.map("SomethingWeNeverSawBefore"))
        .isEqualTo(CategoryType.OTHER);
  }
}
