package com.electronics.store.service;

import com.electronics.store.dto.request.ProductImageRequest;
import com.electronics.store.dto.response.ProductImageResponse;
import com.electronics.store.entity.ProductEntity;
import com.electronics.store.entity.ProductImageEntity;
import com.electronics.store.exception.ResourceNotFoundException;
import com.electronics.store.mapper.ProductImageMapper;
import com.electronics.store.repository.ProductImageRepository;
import com.electronics.store.repository.ProductRepository;
import com.electronics.store.service.impl.ProductImageServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductImageServiceTest {

    @Mock
    private ProductImageRepository productImageRepository;

    @Mock
    private ProductRepository productRepository;

    @Spy
    private ProductImageMapper productImageMapper;

    @InjectMocks
    private ProductImageServiceImpl productImageService;

    private ProductEntity sampleProduct;
    private ProductImageEntity sampleImage1;
    private ProductImageEntity sampleImage2;

    @BeforeEach
    void setUp() {
        sampleProduct = ProductEntity.builder()
                .id(1L)
                .name("Laptop")
                .build();

        sampleImage1 = ProductImageEntity.builder()
                .id(101L)
                .product(sampleProduct)
                .imageUrl("https://example.com/p1.jpg")
                .altText("Front")
                .primary(true)
                .displayOrder(1)
                .createdAt(LocalDateTime.now())
                .build();

        sampleImage2 = ProductImageEntity.builder()
                .id(102L)
                .product(sampleProduct)
                .imageUrl("https://example.com/p2.jpg")
                .altText("Side")
                .primary(false)
                .displayOrder(2)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void getImagesByProductId_Success() {
        when(productRepository.existsById(1L)).thenReturn(true);
        when(productImageRepository.findByProductIdOrderByDisplayOrderAscIdAsc(1L))
                .thenReturn(List.of(sampleImage1, sampleImage2));

        List<ProductImageResponse> result = productImageService.getImagesByProductId(1L);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("https://example.com/p1.jpg", result.get(0).imageUrl());
        assertTrue(result.get(0).primary());
        assertFalse(result.get(1).primary());
    }

    @Test
    void getImagesByProductId_ProductNotFound_ThrowsException() {
        when(productRepository.existsById(99L)).thenReturn(false);

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> productImageService.getImagesByProductId(99L));

        assertEquals("Product not found with id: 99", ex.getMessage());
    }

    @Test
    void getImageById_Success() {
        when(productImageRepository.findById(101L)).thenReturn(Optional.of(sampleImage1));

        ProductImageResponse result = productImageService.getImageById(101L);

        assertNotNull(result);
        assertEquals(101L, result.id());
        assertEquals("https://example.com/p1.jpg", result.imageUrl());
        assertTrue(result.primary());
    }

    @Test
    void getImageById_NotFound_ThrowsException() {
        when(productImageRepository.findById(999L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> productImageService.getImageById(999L));

        assertEquals("Product image not found with id: 999", ex.getMessage());
    }

    @Test
    void addImage_FirstImage_AutoSetsPrimaryTrue() {
        ProductImageRequest request = new ProductImageRequest("https://example.com/p1.jpg", "Front", false, 1);
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));
        when(productImageRepository.existsByProductId(1L)).thenReturn(false);
        when(productImageRepository.save(any(ProductImageEntity.class))).thenAnswer(inv -> {
            ProductImageEntity entity = inv.getArgument(0);
            entity.setId(101L);
            return entity;
        });

        ProductImageResponse result = productImageService.addImage(1L, request);

        assertNotNull(result);
        assertTrue(result.primary());
        assertEquals(101L, result.id());
    }

    @Test
    void addImage_SecondImage_PrimaryFalse_KeepsExistingPrimary() {
        ProductImageRequest request = new ProductImageRequest("https://example.com/p2.jpg", "Side", false, 2);
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));
        when(productImageRepository.existsByProductId(1L)).thenReturn(true);
        when(productImageRepository.save(any(ProductImageEntity.class))).thenAnswer(inv -> {
            ProductImageEntity entity = inv.getArgument(0);
            entity.setId(102L);
            return entity;
        });

        ProductImageResponse result = productImageService.addImage(1L, request);

        assertNotNull(result);
        assertFalse(result.primary());
    }

    @Test
    void addImage_SecondImage_PrimaryTrue_ResetsExistingPrimary() {
        ProductImageRequest request = new ProductImageRequest("https://example.com/p2.jpg", "Side", true, 2);
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));
        when(productImageRepository.existsByProductId(1L)).thenReturn(true);
        when(productImageRepository.findByProductId(1L)).thenReturn(List.of(sampleImage1));
        when(productImageRepository.save(any(ProductImageEntity.class))).thenAnswer(inv -> {
            ProductImageEntity entity = inv.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(102L);
            }
            return entity;
        });

        ProductImageResponse result = productImageService.addImage(1L, request);

        assertNotNull(result);
        assertTrue(result.primary());
        assertFalse(sampleImage1.isPrimary());
    }

    @Test
    void addImage_ProductNotFound_ThrowsException() {
        ProductImageRequest request = new ProductImageRequest("https://example.com/p1.jpg", "Front", false, 1);
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> productImageService.addImage(99L, request));

        assertEquals("Product not found with id: 99", ex.getMessage());
    }

    @Test
    void setPrimaryImage_Success() {
        when(productImageRepository.findById(102L)).thenReturn(Optional.of(sampleImage2));
        when(productImageRepository.findByProductId(1L)).thenReturn(List.of(sampleImage1, sampleImage2));
        when(productImageRepository.save(any(ProductImageEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductImageResponse result = productImageService.setPrimaryImage(102L);

        assertNotNull(result);
        assertTrue(result.primary());
        assertFalse(sampleImage1.isPrimary());
        assertTrue(sampleImage2.isPrimary());
    }

    @Test
    void setPrimaryImage_NotFound_ThrowsException() {
        when(productImageRepository.findById(999L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> productImageService.setPrimaryImage(999L));

        assertEquals("Product image not found with id: 999", ex.getMessage());
    }

    @Test
    void updateImage_Success() {
        ProductImageRequest request = new ProductImageRequest("https://example.com/p1-updated.jpg", "New Front", false, 5);
        when(productImageRepository.findById(101L)).thenReturn(Optional.of(sampleImage1));
        when(productImageRepository.save(sampleImage1)).thenReturn(sampleImage1);

        ProductImageResponse result = productImageService.updateImage(101L, request);

        assertNotNull(result);
        assertEquals("https://example.com/p1-updated.jpg", result.imageUrl());
        assertEquals("New Front", result.altText());
        assertEquals(5, result.displayOrder());
    }

    @Test
    void updateImage_SetPrimaryTrue_ResetsOtherImages() {
        ProductImageRequest request = new ProductImageRequest("https://example.com/p2.jpg", "Side", true, 2);
        when(productImageRepository.findById(102L)).thenReturn(Optional.of(sampleImage2));
        when(productImageRepository.findByProductId(1L)).thenReturn(List.of(sampleImage1, sampleImage2));
        when(productImageRepository.save(any(ProductImageEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductImageResponse result = productImageService.updateImage(102L, request);

        assertNotNull(result);
        assertTrue(result.primary());
        assertFalse(sampleImage1.isPrimary());
    }

    @Test
    void deleteImage_NonPrimary_Success() {
        when(productImageRepository.findById(102L)).thenReturn(Optional.of(sampleImage2));

        productImageService.deleteImage(102L);

        verify(productImageRepository).deleteById(102L);
    }

    @Test
    void deleteImage_Primary_AssignsNewPrimaryToNextImage() {
        when(productImageRepository.findById(101L)).thenReturn(Optional.of(sampleImage1));
        when(productImageRepository.findFirstByProductIdOrderByDisplayOrderAscIdAsc(1L))
                .thenReturn(Optional.of(sampleImage2));

        productImageService.deleteImage(101L);

        verify(productImageRepository).deleteById(101L);
        assertTrue(sampleImage2.isPrimary());
        verify(productImageRepository).save(sampleImage2);
    }

    @Test
    void deleteImage_NotFound_ThrowsException() {
        when(productImageRepository.findById(999L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> productImageService.deleteImage(999L));

        assertEquals("Product image not found with id: 999", ex.getMessage());
    }
}
