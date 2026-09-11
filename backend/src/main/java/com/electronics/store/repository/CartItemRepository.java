package com.electronics.store.repository;

import com.electronics.store.entity.CartItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CartItemRepository extends JpaRepository<CartItemEntity, Long> {
    Optional<CartItemEntity> findByCartIdAndProductId(Long cartId, Long productId);
    // Checkout keeps this query lazy so it loads live product state only after locking products.
    List<CartItemEntity> findByCartIdOrderByIdAsc(Long cartId);

    @EntityGraph(attributePaths = "product")
    @Query("select i from CartItemEntity i where i.cart.id = :cartId order by i.id")
    List<CartItemEntity> findForDisplayByCartId(@Param("cartId") Long cartId);
    void deleteByCartId(Long cartId);
}
