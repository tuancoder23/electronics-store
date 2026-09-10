package com.electronics.store.repository;

import com.electronics.store.entity.PaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<PaymentEntity, Long> {
    Optional<PaymentEntity> findByOrderId(Long orderId);

    boolean existsByOrderId(Long orderId);

    // Only select the ID before locking Order: avoid caching stale payment state in the persistence context.
    @Query("select p.order.id from PaymentEntity p where p.gatewayReference = :reference")
    Optional<Long> findOrderIdByGatewayReference(@Param("reference") String reference);
}
