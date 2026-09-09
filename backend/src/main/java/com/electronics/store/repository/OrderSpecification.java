package com.electronics.store.repository;

import com.electronics.store.dto.request.OrderSearchCriteria;
import com.electronics.store.entity.OrderEntity;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class OrderSpecification {
    private OrderSpecification() {
    }

    public static Specification<OrderEntity> withCriteria(OrderSearchCriteria criteria) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (criteria.status() != null) {
                predicates.add(builder.equal(root.get("status"), criteria.status()));
            }
            if (criteria.userId() != null) {
                predicates.add(builder.equal(root.get("user").get("id"), criteria.userId()));
            }
            if (criteria.keyword() != null && !criteria.keyword().isBlank()) {
                String keyword = criteria.keyword().trim().toLowerCase(Locale.ROOT)
                        .replace("!", "!!").replace("%", "!%").replace("_", "!_");
                String pattern = "%" + keyword + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("receiverName")), pattern, '!'),
                        builder.like(builder.lower(root.get("phone")), pattern, '!')));
            }
            if (criteria.fromDate() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), criteria.fromDate()));
            }
            if (criteria.toDate() != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("createdAt"), criteria.toDate()));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
