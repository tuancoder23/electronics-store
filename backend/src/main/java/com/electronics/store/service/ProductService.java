package com.electronics.store.service;

import com.electronics.store.dto.request.ProductRequest;
import com.electronics.store.dto.request.ProductSearchCriteria;
import com.electronics.store.dto.response.PagedResponse;
import com.electronics.store.dto.response.ProductResponse;

/**
 * Service interface for Product operations.
 */
public interface ProductService {

    PagedResponse<ProductResponse> searchProducts(ProductSearchCriteria criteria);

    ProductResponse getProductById(Long id);

    ProductResponse createProduct(ProductRequest request);

    ProductResponse updateProduct(Long id, ProductRequest request);

    void deleteProduct(Long id);
}
