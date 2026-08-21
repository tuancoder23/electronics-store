package com.electronics.store.service.impl;

import com.electronics.store.dto.request.ProductRequest;
import com.electronics.store.dto.response.ProductResponse;
import com.electronics.store.entity.BrandEntity;
import com.electronics.store.entity.CategoryEntity;
import com.electronics.store.entity.ProductEntity;
import com.electronics.store.entity.ProductStatus;
import com.electronics.store.exception.DuplicateResourceException;
import com.electronics.store.exception.ResourceNotFoundException;
import com.electronics.store.mapper.ProductMapper;
import com.electronics.store.repository.BrandRepository;
import com.electronics.store.repository.CategoryRepository;
import com.electronics.store.repository.ProductRepository;
import com.electronics.store.service.ProductService;
import com.electronics.store.util.SlugUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final ProductMapper productMapper;

    @Override
    public List<ProductResponse> getAllProducts() {
        return productRepository.findAll().stream()
                .map(productMapper::toResponse)
                .toList();
    }

    @Override
    public ProductResponse getProductById(Long id) {
        ProductEntity entity = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        return productMapper.toResponse(entity);
    }

    @Override
    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        validatePrices(request);

        CategoryEntity category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + request.categoryId()));

        BrandEntity brand = brandRepository.findById(request.brandId())
                .orElseThrow(() -> new ResourceNotFoundException("Brand not found with id: " + request.brandId()));

        String trimmedName = request.name().trim();
        if (productRepository.existsByName(trimmedName)) {
            throw new DuplicateResourceException("Product name already exists: " + trimmedName);
        }

        String slug = generateUniqueSlug(trimmedName, null);
        ProductEntity entity = productMapper.toEntity(request, slug, category, brand);
        ProductEntity saved = productRepository.save(entity);

        return productMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public ProductResponse updateProduct(Long id, ProductRequest request) {
        ProductEntity entity = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        validatePrices(request);

        CategoryEntity category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + request.categoryId()));

        BrandEntity brand = brandRepository.findById(request.brandId())
                .orElseThrow(() -> new ResourceNotFoundException("Brand not found with id: " + request.brandId()));

        String trimmedName = request.name().trim();
        if (productRepository.existsByNameAndIdNot(trimmedName, id)) {
            throw new DuplicateResourceException("Product name already exists: " + trimmedName);
        }

        if (!entity.getName().equals(trimmedName)) {
            entity.setName(trimmedName);
            entity.setSlug(generateUniqueSlug(trimmedName, id));
        }

        entity.setDescription(request.description() != null ? request.description().trim() : null);
        entity.setPrice(request.price());
        entity.setDiscountPrice(request.discountPrice());
        entity.setQuantity(request.quantity());
        entity.setThumbnailUrl(request.thumbnailUrl() != null ? request.thumbnailUrl().trim() : null);
        entity.setStatus(request.status() != null ? request.status() : ProductStatus.ACTIVE);
        entity.setCategory(category);
        entity.setBrand(brand);

        ProductEntity updated = productRepository.save(entity);
        return productMapper.toResponse(updated);
    }

    @Override
    @Transactional
    public void deleteProduct(Long id) {
        if (!productRepository.existsById(id)) {
            throw new ResourceNotFoundException("Product not found with id: " + id);
        }
        productRepository.deleteById(id);
    }

    private void validatePrices(ProductRequest request) {
        if (request.discountPrice() != null && request.price() != null) {
            if (request.discountPrice().compareTo(request.price()) > 0) {
                throw new IllegalArgumentException("Discount price cannot be greater than regular price");
            }
        }
    }

    private String generateUniqueSlug(String name, Long currentId) {
        String baseSlug = SlugUtils.toSlug(name);
        String slug = baseSlug;
        int counter = 1;
        while (currentId == null
                ? productRepository.existsBySlug(slug)
                : productRepository.existsBySlugAndIdNot(slug, currentId)) {
            slug = baseSlug + "-" + counter;
            counter++;
        }
        return slug;
    }
}
