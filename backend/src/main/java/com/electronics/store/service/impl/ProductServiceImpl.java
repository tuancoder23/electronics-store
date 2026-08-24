package com.electronics.store.service.impl;

import com.electronics.store.dto.request.ProductRequest;
import com.electronics.store.dto.request.ProductSearchCriteria;
import com.electronics.store.dto.response.PagedResponse;
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
import com.electronics.store.repository.ProductSpecification;
import com.electronics.store.service.ProductService;
import com.electronics.store.util.SlugUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("price", "name", "createdAt");

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final ProductMapper productMapper;

    @Override
    public PagedResponse<ProductResponse> searchProducts(ProductSearchCriteria criteria) {
        validateSearchCriteria(criteria);
        Pageable pageable = PageRequest.of(criteria.page(), criteria.size(), parseSort(criteria.sort()));
        Page<ProductResponse> products = productRepository
                .findAll(ProductSpecification.withCriteria(criteria), pageable)
                .map(productMapper::toResponse);
        return PagedResponse.from(products);
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

    private void validateSearchCriteria(ProductSearchCriteria criteria) {
        if (criteria.page() < 0) {
            throw new IllegalArgumentException("Page must be greater than or equal to 0");
        }
        if (criteria.size() <= 0) {
            throw new IllegalArgumentException("Size must be greater than 0");
        }
        if (criteria.size() > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Size must not exceed " + MAX_PAGE_SIZE);
        }
        if (criteria.minPrice() != null && criteria.minPrice().signum() < 0) {
            throw new IllegalArgumentException("Minimum price must be greater than or equal to 0");
        }
        if (criteria.maxPrice() != null && criteria.maxPrice().signum() < 0) {
            throw new IllegalArgumentException("Maximum price must be greater than or equal to 0");
        }
        if (criteria.minPrice() != null && criteria.maxPrice() != null
                && criteria.minPrice().compareTo(criteria.maxPrice()) > 0) {
            throw new IllegalArgumentException("Minimum price must not be greater than maximum price");
        }
    }

    private Sort parseSort(String sortValue) {
        String value = sortValue == null || sortValue.isBlank() ? "createdAt,desc" : sortValue.trim();
        String[] parts = value.split(",", -1);
        if (parts.length != 2 || !ALLOWED_SORT_FIELDS.contains(parts[0])) {
            throw new IllegalArgumentException("Sort must use an allowed field: price, name, createdAt");
        }

        Sort.Direction direction;
        try {
            direction = Sort.Direction.fromString(parts[1].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Sort direction must be asc or desc");
        }
        return Sort.by(direction, parts[0]);
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
