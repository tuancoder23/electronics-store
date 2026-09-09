package com.electronics.store.repository;

import com.electronics.store.entity.OrderEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<OrderEntity, Long> {
    Page<OrderEntity> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = "items")
    Optional<OrderEntity> findByIdAndUserId(Long id, Long userId);
}
