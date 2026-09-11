package com.electronics.store.repository;

import com.electronics.store.entity.WishlistItemEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WishlistItemRepository extends JpaRepository<WishlistItemEntity, Long> {
    @EntityGraph(attributePaths = {"product", "product.brand", "product.category"})
    Page<WishlistItemEntity> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"product", "product.brand", "product.category"})
    Optional<WishlistItemEntity> findByUserIdAndProductId(Long userId, Long productId);
}
