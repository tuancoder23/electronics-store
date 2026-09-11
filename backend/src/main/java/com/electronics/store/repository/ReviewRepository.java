package com.electronics.store.repository;

import com.electronics.store.dto.response.ProductRatingSummaryResponse;
import com.electronics.store.entity.ReviewEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<ReviewEntity, Long> {
    @EntityGraph(attributePaths = "user")
    Page<ReviewEntity> findByProductIdOrderByCreatedAtDescIdDesc(Long productId, Pageable pageable);

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    Optional<ReviewEntity> findByIdAndUserId(Long id, Long userId);

    Optional<ReviewEntity> findByUserIdAndProductId(Long userId, Long productId);

    long countByProductId(Long productId);

    @Query("""
            select new com.electronics.store.dto.response.ProductRatingSummaryResponse(avg(r.rating), count(r))
            from ReviewEntity r where r.product.id = :productId
            """)
    ProductRatingSummaryResponse getProductRatingSummary(@Param("productId") Long productId);
}
