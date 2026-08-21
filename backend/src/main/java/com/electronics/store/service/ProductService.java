package com.electronics.store.service;

import com.electronics.store.dto.request.ProductRequest;
import com.electronics.store.dto.response.ProductResponse;

import java.util.List;

/**
 * Service interface for Product operations.
 */
public interface ProductService {

    List<ProductResponse> getAllProducts();

    ProductResponse getProductById(Long id);

    ProductResponse createProduct(ProductRequest request);

    ProductResponse updateProduct(Long id, ProductRequest request);

    void deleteProduct(Long id);
}
