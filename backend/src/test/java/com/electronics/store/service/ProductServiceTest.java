package com.electronics.store.service;

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
import com.electronics.store.service.impl.ProductServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private BrandRepository brandRepository;

    @Spy
    private ProductMapper productMapper;

    @InjectMocks
    private ProductServiceImpl productService;

    private CategoryEntity sampleCategory;
    private BrandEntity sampleBrand;
    private ProductEntity sampleProduct;

    @BeforeEach
    void setUp() {
        sampleCategory = CategoryEntity.builder()
                .id(1L)
                .name("Laptop")
                .slug("laptop")
                .description("All laptops")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        sampleBrand = BrandEntity.builder()
                .id(1L)
                .name("ASUS")
                .slug("asus")
                .description("ASUS products")
                .logoUrl("https://example.com/asus.png")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        sampleProduct = ProductEntity.builder()
                .id(1L)
                .name("ASUS TUF Gaming F15")
                .slug("asus-tuf-gaming-f15")
                .description("Gaming laptop")
                .price(new BigDecimal("25990000"))
                .discountPrice(new BigDecimal("23990000"))
                .quantity(10)
                .thumbnailUrl("https://example.com/tuf.png")
                .status(ProductStatus.ACTIVE)
                .category(sampleCategory)
                .brand(sampleBrand)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void getAllProducts_shouldReturnList() {
        when(productRepository.findAll()).thenReturn(List.of(sampleProduct));

        List<ProductResponse> result = productService.getAllProducts();

        assertEquals(1, result.size());
        assertEquals("ASUS TUF Gaming F15", result.get(0).name());
        assertEquals("asus-tuf-gaming-f15", result.get(0).slug());
        assertEquals(new BigDecimal("25990000"), result.get(0).price());
        assertEquals("Laptop", result.get(0).category().name());
        assertEquals("ASUS", result.get(0).brand().name());
    }

    @Test
    void getProductById_whenFound_shouldReturnResponse() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));

        ProductResponse result = productService.getProductById(1L);

        assertNotNull(result);
        assertEquals(1L, result.id());
        assertEquals("ASUS TUF Gaming F15", result.name());
        assertEquals(ProductStatus.ACTIVE, result.status());
    }

    @Test
    void getProductById_whenNotFound_shouldThrowResourceNotFoundException() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> productService.getProductById(99L));
    }

    @Test
    void createProduct_shouldSucceed() {
        ProductRequest request = new ProductRequest(
                "ASUS ROG Zephyrus G14",
                "Compact gaming laptop",
                new BigDecimal("35000000"),
                new BigDecimal("33000000"),
                5,
                null,
                ProductStatus.ACTIVE,
                1L,
                1L
        );

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(sampleCategory));
        when(brandRepository.findById(1L)).thenReturn(Optional.of(sampleBrand));
        when(productRepository.existsByName("ASUS ROG Zephyrus G14")).thenReturn(false);
        when(productRepository.existsBySlug("asus-rog-zephyrus-g14")).thenReturn(false);
        when(productRepository.save(any(ProductEntity.class))).thenAnswer(invocation -> {
            ProductEntity entity = invocation.getArgument(0);
            entity.setId(2L);
            entity.setCreatedAt(LocalDateTime.now());
            entity.setUpdatedAt(LocalDateTime.now());
            return entity;
        });

        ProductResponse result = productService.createProduct(request);

        assertNotNull(result);
        assertEquals("ASUS ROG Zephyrus G14", result.name());
        assertEquals("asus-rog-zephyrus-g14", result.slug());
        assertEquals(new BigDecimal("35000000"), result.price());
        assertEquals(1L, result.category().id());
        assertEquals(1L, result.brand().id());
    }

    @Test
    void createProduct_whenCategoryNotFound_shouldThrowResourceNotFoundException() {
        ProductRequest request = new ProductRequest(
                "Product", "Desc", new BigDecimal("1000"), null, 1, null, ProductStatus.ACTIVE, 99L, 1L
        );

        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> productService.createProduct(request));
    }

    @Test
    void createProduct_whenBrandNotFound_shouldThrowResourceNotFoundException() {
        ProductRequest request = new ProductRequest(
                "Product", "Desc", new BigDecimal("1000"), null, 1, null, ProductStatus.ACTIVE, 1L, 99L
        );

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(sampleCategory));
        when(brandRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> productService.createProduct(request));
    }

    @Test
    void createProduct_whenDuplicateName_shouldThrowDuplicateResourceException() {
        ProductRequest request = new ProductRequest(
                "ASUS TUF Gaming F15", "Desc", new BigDecimal("25000000"), null, 10, null, ProductStatus.ACTIVE, 1L, 1L
        );

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(sampleCategory));
        when(brandRepository.findById(1L)).thenReturn(Optional.of(sampleBrand));
        when(productRepository.existsByName("ASUS TUF Gaming F15")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> productService.createProduct(request));
    }

    @Test
    void createProduct_whenDiscountPriceGreaterThanPrice_shouldThrowIllegalArgumentException() {
        ProductRequest request = new ProductRequest(
                "Product", "Desc", new BigDecimal("10000000"), new BigDecimal("15000000"), 5, null, ProductStatus.ACTIVE, 1L, 1L
        );

        assertThrows(IllegalArgumentException.class, () -> productService.createProduct(request));
    }

    @Test
    void createProduct_whenSlugExists_shouldAppendSuffix() {
        ProductRequest request = new ProductRequest(
                "ASUS TUF Gaming F15", "Desc", new BigDecimal("25000000"), null, 10, null, ProductStatus.ACTIVE, 1L, 1L
        );

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(sampleCategory));
        when(brandRepository.findById(1L)).thenReturn(Optional.of(sampleBrand));
        when(productRepository.existsByName("ASUS TUF Gaming F15")).thenReturn(false);
        when(productRepository.existsBySlug("asus-tuf-gaming-f15")).thenReturn(true);
        when(productRepository.existsBySlug("asus-tuf-gaming-f15-1")).thenReturn(false);
        when(productRepository.save(any(ProductEntity.class))).thenAnswer(invocation -> {
            ProductEntity entity = invocation.getArgument(0);
            entity.setId(3L);
            return entity;
        });

        ProductResponse result = productService.createProduct(request);

        assertEquals("asus-tuf-gaming-f15-1", result.slug());
    }

    @Test
    void updateProduct_shouldSucceed() {
        ProductRequest request = new ProductRequest(
                "ASUS TUF Gaming F15 2024",
                "Updated laptop",
                new BigDecimal("26990000"),
                new BigDecimal("24990000"),
                8,
                "https://example.com/updated.png",
                ProductStatus.ACTIVE,
                1L,
                1L
        );

        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(sampleCategory));
        when(brandRepository.findById(1L)).thenReturn(Optional.of(sampleBrand));
        when(productRepository.existsByNameAndIdNot("ASUS TUF Gaming F15 2024", 1L)).thenReturn(false);
        when(productRepository.existsBySlugAndIdNot("asus-tuf-gaming-f15-2024", 1L)).thenReturn(false);
        when(productRepository.save(any(ProductEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponse result = productService.updateProduct(1L, request);

        assertEquals("ASUS TUF Gaming F15 2024", result.name());
        assertEquals("asus-tuf-gaming-f15-2024", result.slug());
        assertEquals(new BigDecimal("26990000"), result.price());
        assertEquals(8, result.quantity());
    }

    @Test
    void updateProduct_whenProductNotFound_shouldThrowResourceNotFoundException() {
        ProductRequest request = new ProductRequest(
                "Product", "Desc", new BigDecimal("1000"), null, 1, null, ProductStatus.ACTIVE, 1L, 1L
        );

        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> productService.updateProduct(99L, request));
    }

    @Test
    void updateProduct_whenDiscountPriceGreaterThanPrice_shouldThrowIllegalArgumentException() {
        ProductRequest request = new ProductRequest(
                "Product", "Desc", new BigDecimal("1000"), new BigDecimal("2000"), 1, null, ProductStatus.ACTIVE, 1L, 1L
        );

        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));

        assertThrows(IllegalArgumentException.class, () -> productService.updateProduct(1L, request));
    }

    @Test
    void deleteProduct_whenFound_shouldDelete() {
        when(productRepository.existsById(1L)).thenReturn(true);

        productService.deleteProduct(1L);

        verify(productRepository).deleteById(1L);
    }

    @Test
    void deleteProduct_whenNotFound_shouldThrow() {
        when(productRepository.existsById(99L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> productService.deleteProduct(99L));
    }
}
