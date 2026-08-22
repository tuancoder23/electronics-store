package com.electronics.store.service;

import com.electronics.store.dto.request.ProductImageRequest;
import com.electronics.store.dto.response.ProductImageResponse;

import java.util.List;

/**
 * Service interface for Product Image operations.
 */
public interface ProductImageService {

    List<ProductImageResponse> getImagesByProductId(Long productId);

    ProductImageResponse getImageById(Long imageId);

    ProductImageResponse addImage(Long productId, ProductImageRequest request);

    ProductImageResponse updateImage(Long imageId, ProductImageRequest request);

    ProductImageResponse setPrimaryImage(Long imageId);

    void deleteImage(Long imageId);
}
