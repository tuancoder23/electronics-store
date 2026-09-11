package com.electronics.store.repository;

import com.electronics.store.entity.OrderItemEntity;
import com.electronics.store.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItemEntity, Long> {
    boolean existsByOrderUserIdAndProductIdAndOrderStatus(Long userId, Long productId, OrderStatus status);

    boolean existsByOrderUserIdAndProductId(Long userId, Long productId);
}
