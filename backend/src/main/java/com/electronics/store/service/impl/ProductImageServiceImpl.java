package com.electronics.store.service.impl;

import com.electronics.store.dto.request.ProductImageRequest;
import com.electronics.store.dto.response.ProductImageResponse;
import com.electronics.store.entity.ProductEntity;
import com.electronics.store.entity.ProductImageEntity;
import com.electronics.store.exception.ResourceNotFoundException;
import com.electronics.store.mapper.ProductImageMapper;
import com.electronics.store.repository.ProductImageRepository;
import com.electronics.store.repository.ProductRepository;
import com.electronics.store.service.ProductImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductImageServiceImpl implements ProductImageService {

    private final ProductImageRepository productImageRepository;
    private final ProductRepository productRepository;
    private final ProductImageMapper productImageMapper;

    @Override
    public List<ProductImageResponse> getImagesByProductId(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Product not found with id: " + productId);
        }

        return productImageRepository.findByProductIdOrderByDisplayOrderAscIdAsc(productId).stream()
                .map(productImageMapper::toResponse)
                .toList();
    }

    @Override
    public ProductImageResponse getImageById(Long imageId) {
        ProductImageEntity image = productImageRepository.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Product image not found with id: " + imageId));

        return productImageMapper.toResponse(image);
    }

    @Override
    @Transactional
    public ProductImageResponse addImage(Long productId, ProductImageRequest request) {
        ProductEntity product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        boolean hasExistingImages = productImageRepository.existsByProductId(productId);
        boolean shouldBePrimary = !hasExistingImages || Boolean.TRUE.equals(request.primary());

        if (shouldBePrimary && hasExistingImages) {
            resetPrimaryImages(productId, null);
        }

        ProductImageEntity entity = productImageMapper.toEntity(request, product, shouldBePrimary);
        ProductImageEntity saved = productImageRepository.save(entity);

        return productImageMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public ProductImageResponse updateImage(Long imageId, ProductImageRequest request) {
        ProductImageEntity image = productImageRepository.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Product image not found with id: " + imageId));

        image.setImageUrl(request.imageUrl().trim());
        image.setAltText(request.altText() != null ? request.altText().trim() : null);
        image.setDisplayOrder(request.displayOrder() != null ? request.displayOrder() : 0);

        if (Boolean.TRUE.equals(request.primary()) && !image.isPrimary()) {
            resetPrimaryImages(image.getProduct().getId(), imageId);
            image.setPrimary(true);
        }

        ProductImageEntity updated = productImageRepository.save(image);
        return productImageMapper.toResponse(updated);
    }

    @Override
    @Transactional
    public ProductImageResponse setPrimaryImage(Long imageId) {
        ProductImageEntity image = productImageRepository.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Product image not found with id: " + imageId));

        Long productId = image.getProduct().getId();
        resetPrimaryImages(productId, imageId);

        image.setPrimary(true);
        ProductImageEntity updated = productImageRepository.save(image);

        return productImageMapper.toResponse(updated);
    }

    @Override
    @Transactional
    public void deleteImage(Long imageId) {
        ProductImageEntity image = productImageRepository.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Product image not found with id: " + imageId));

        Long productId = image.getProduct().getId();
        boolean wasPrimary = image.isPrimary();

        productImageRepository.deleteById(imageId);
        productImageRepository.flush();

        if (wasPrimary) {
            Optional<ProductImageEntity> nextPrimary = productImageRepository.findFirstByProductIdOrderByDisplayOrderAscIdAsc(productId);
            nextPrimary.ifPresent(next -> {
                next.setPrimary(true);
                productImageRepository.save(next);
            });
        }
    }

    private void resetPrimaryImages(Long productId, Long excludeImageId) {
        List<ProductImageEntity> images = productImageRepository.findByProductId(productId);
        for (ProductImageEntity img : images) {
            if ((excludeImageId == null || !img.getId().equals(excludeImageId)) && img.isPrimary()) {
                img.setPrimary(false);
                productImageRepository.save(img);
            }
        }
    }
}
