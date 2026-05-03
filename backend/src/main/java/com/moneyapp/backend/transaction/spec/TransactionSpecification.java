package com.moneyapp.backend.transaction.spec;

import com.moneyapp.backend.transaction.dto.TransactionFilter;
import com.moneyapp.backend.transaction.entity.Transaction;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public final class TransactionSpecification {

  private TransactionSpecification() {}

  public static Specification<Transaction> forUserWithFilters(
      Long userId, TransactionFilter filter) {
    return (root, query, criteriaBuilder) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(criteriaBuilder.equal(root.get("userId"), userId));

      if (filter.accountId() != null) {
        predicates.add(criteriaBuilder.equal(root.get("accountId"), filter.accountId()));
      }
      if (!isBlank(filter.category())) {
        predicates.add(criteriaBuilder.equal(root.get("category"), filter.category()));
      }
      if (filter.minDate() != null) {
        predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("date"), filter.minDate()));
      }
      if (filter.maxDate() != null) {
        predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("date"), filter.maxDate()));
      }
      if (filter.minAmount() != null) {
        predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("value"), filter.minAmount()));
      }
      if (filter.maxAmount() != null) {
        predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("value"), filter.maxAmount()));
      }
      if (!isBlank(filter.keyword())) {
        String keyword = "%" + filter.keyword().trim().toLowerCase() + "%";
        predicates.add(
            criteriaBuilder.or(
                criteriaBuilder.like(criteriaBuilder.lower(root.get("label")), keyword),
                criteriaBuilder.like(criteriaBuilder.lower(root.get("wording")), keyword)));
      }

      return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
    };
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
