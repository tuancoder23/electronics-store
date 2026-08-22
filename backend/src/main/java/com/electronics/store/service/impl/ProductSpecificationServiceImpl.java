package com.electronics.store.service.impl;

import com.electronics.store.dto.request.ProductSpecificationRequest;
import com.electronics.store.dto.response.ProductSpecificationResponse;
import com.electronics.store.entity.ProductEntity;
import com.electronics.store.entity.ProductSpecificationEntity;
import com.electronics.store.exception.DuplicateResourceException;
import com.electronics.store.exception.ResourceNotFoundException;
import com.electronics.store.mapper.ProductSpecificationMapper;
import com.electronics.store.repository.ProductRepository;
import com.electronics.store.repository.ProductSpecificationRepository;
import com.electronics.store.service.ProductSpecificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductSpecificationServiceImpl implements ProductSpecificationService {

    private final ProductSpecificationRepository productSpecificationRepository;
    private final ProductRepository productRepository;
    private final ProductSpecificationMapper productSpecificationMapper;

    @Override
    public List<ProductSpecificationResponse> getSpecificationsByProductId(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Product not found with id: " + productId);
        }

        return productSpecificationRepository.findByProductIdOrderByDisplayOrderAscIdAsc(productId).stream()
                .map(productSpecificationMapper::toResponse)
                .toList();
    }

    @Override
    public ProductSpecificationResponse getSpecificationById(Long id) {
        ProductSpecificationEntity entity = productSpecificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product specification not found with id: " + id));

        return productSpecificationMapper.toResponse(entity);
    }

    @Override
    @Transactional
    public ProductSpecificationResponse createSpecification(Long productId, ProductSpecificationRequest request) {
        ProductEntity product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        String trimmedName = request.specName().trim();
        if (productSpecificationRepository.existsByProductIdAndSpecNameIgnoreCase(productId, trimmedName)) {
            throw new DuplicateResourceException("Specification '" + trimmedName + "' already exists for product with id: " + productId);
        }

        ProductSpecificationEntity entity = productSpecificationMapper.toEntity(request, product);
        ProductSpecificationEntity saved = productSpecificationRepository.save(entity);

        return productSpecificationMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public ProductSpecificationResponse updateSpecification(Long id, ProductSpecificationRequest request) {
        ProductSpecificationEntity entity = productSpecificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product specification not found with id: " + id));

        String trimmedName = request.specName().trim();
        if (productSpecificationRepository.existsByProductIdAndSpecNameIgnoreCaseAndIdNot(entity.getProduct().getId(), trimmedName, id)) {
            throw new DuplicateResourceException("Specification '" + trimmedName + "' already exists for product with id: " + entity.getProduct().getId());
        }

        entity.setSpecName(trimmedName);
        entity.setSpecValue(request.specValue().trim());
        entity.setDisplayOrder(request.displayOrder() != null ? request.displayOrder() : 0);

        ProductSpecificationEntity updated = productSpecificationRepository.save(entity);
        return productSpecificationMapper.toResponse(updated);
    }

    @Override
    @Transactional
    public void deleteSpecification(Long id) {
        if (!productSpecificationRepository.existsById(id)) {
            throw new ResourceNotFoundException("Product specification not found with id: " + id);
        }
        productSpecificationRepository.deleteById(id);
    }
}
