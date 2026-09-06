package com.electronics.store.repository;

import com.electronics.store.entity.CartItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CartItemRepository extends JpaRepository<CartItemEntity, Long> {
    Optional<CartItemEntity> findByCartIdAndProductId(Long cartId, Long productId);
    List<CartItemEntity> findByCartId(Long cartId);
    List<CartItemEntity> findByCartIdOrderByIdAsc(Long cartId);
    void deleteByCartId(Long cartId);
}
