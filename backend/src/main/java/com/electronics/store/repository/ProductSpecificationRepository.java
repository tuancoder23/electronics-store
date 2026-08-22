package com.electronics.store.repository;

import com.electronics.store.entity.ProductSpecificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductSpecificationRepository extends JpaRepository<ProductSpecificationEntity, Long> {

    List<ProductSpecificationEntity> findByProductIdOrderByDisplayOrderAscIdAsc(Long productId);

    boolean existsByProductIdAndSpecNameIgnoreCase(Long productId, String specName);

    boolean existsByProductIdAndSpecNameIgnoreCaseAndIdNot(Long productId, String specName, Long id);

    void deleteByProductId(Long productId);
}
