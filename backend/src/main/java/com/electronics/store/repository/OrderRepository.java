package com.electronics.store.repository;

import com.electronics.store.entity.OrderEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<OrderEntity, Long>, JpaSpecificationExecutor<OrderEntity> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrderEntity o where o.id = :id")
    Optional<OrderEntity> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrderEntity o where o.id = :id and o.user.id = :userId")
    Optional<OrderEntity> findByIdAndUserIdForUpdate(@Param("id") Long id, @Param("userId") Long userId);

    Page<OrderEntity> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = "items")
    Optional<OrderEntity> findByIdAndUserId(Long id, Long userId);
}
