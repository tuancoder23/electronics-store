package com.electronics.store.repository;

import com.electronics.store.entity.ProductImageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImageEntity, Long> {

    List<ProductImageEntity> findByProductIdOrderByDisplayOrderAscIdAsc(Long productId);

    List<ProductImageEntity> findByProductId(Long productId);

    Optional<ProductImageEntity> findByProductIdAndPrimaryTrue(Long productId);

    Optional<ProductImageEntity> findFirstByProductIdOrderByDisplayOrderAscIdAsc(Long productId);

    boolean existsByProductId(Long productId);

    void deleteByProductId(Long productId);
}
