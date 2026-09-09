package com.electronics.store.dto.request;

import com.electronics.store.entity.OrderStatus;

import java.time.LocalDateTime;

public record OrderSearchCriteria(OrderStatus status, Long userId, String keyword,
                                  LocalDateTime fromDate, LocalDateTime toDate) {
}
