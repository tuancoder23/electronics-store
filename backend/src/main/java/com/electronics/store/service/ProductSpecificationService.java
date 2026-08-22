package com.electronics.store.service;

import com.electronics.store.dto.request.ProductSpecificationRequest;
import com.electronics.store.dto.response.ProductSpecificationResponse;

import java.util.List;

/**
 * Service interface for Product Specification operations.
 */
public interface ProductSpecificationService {

    List<ProductSpecificationResponse> getSpecificationsByProductId(Long productId);

    ProductSpecificationResponse getSpecificationById(Long id);

    ProductSpecificationResponse createSpecification(Long productId, ProductSpecificationRequest request);

    ProductSpecificationResponse updateSpecification(Long id, ProductSpecificationRequest request);

    void deleteSpecification(Long id);
}
