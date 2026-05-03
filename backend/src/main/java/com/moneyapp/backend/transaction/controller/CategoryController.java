package com.moneyapp.backend.transaction.controller;

import com.moneyapp.backend.transaction.enums.CategoryType;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

  @GetMapping
  public ResponseEntity<List<String>> getCategories() {
    return ResponseEntity.ok(Arrays.stream(CategoryType.values()).map(CategoryType::name).toList());
  }
}
