package com.electronics.store.service;

import com.electronics.store.dto.request.ProductSpecificationRequest;
import com.electronics.store.dto.response.ProductSpecificationResponse;
import com.electronics.store.entity.ProductEntity;
import com.electronics.store.entity.ProductSpecificationEntity;
import com.electronics.store.exception.DuplicateResourceException;
import com.electronics.store.exception.ResourceNotFoundException;
import com.electronics.store.mapper.ProductSpecificationMapper;
import com.electronics.store.repository.ProductRepository;
import com.electronics.store.repository.ProductSpecificationRepository;
import com.electronics.store.service.impl.ProductSpecificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductSpecificationServiceTest {

    @Mock
    private ProductSpecificationRepository productSpecificationRepository;

    @Mock
    private ProductRepository productRepository;

    @Spy
    private ProductSpecificationMapper productSpecificationMapper;

    @InjectMocks
    private ProductSpecificationServiceImpl productSpecificationService;

    private ProductEntity sampleProduct;
    private ProductSpecificationEntity sampleSpec;

    @BeforeEach
    void setUp() {
        sampleProduct = ProductEntity.builder()
                .id(1L)
                .name("ASUS TUF Gaming F15")
                .slug("asus-tuf-gaming-f15")
                .build();

        sampleSpec = ProductSpecificationEntity.builder()
                .id(10L)
                .product(sampleProduct)
                .specName("CPU")
                .specValue("Intel Core i7-13620H")
                .displayOrder(1)
                .build();
    }

    @Test
    void getSpecificationsByProductId_Success() {
        when(productRepository.existsById(1L)).thenReturn(true);
        when(productSpecificationRepository.findByProductIdOrderByDisplayOrderAscIdAsc(1L))
                .thenReturn(List.of(sampleSpec));

        List<ProductSpecificationResponse> result = productSpecificationService.getSpecificationsByProductId(1L);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("CPU", result.get(0).specName());
        assertEquals(1L, result.get(0).productId());
        verify(productRepository).existsById(1L);
        verify(productSpecificationRepository).findByProductIdOrderByDisplayOrderAscIdAsc(1L);
    }

    @Test
    void getSpecificationsByProductId_ProductNotFound_ThrowsException() {
        when(productRepository.existsById(99L)).thenReturn(false);

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> productSpecificationService.getSpecificationsByProductId(99L));

        assertEquals("Product not found with id: 99", ex.getMessage());
    }

    @Test
    void getSpecificationById_Success() {
        when(productSpecificationRepository.findById(10L)).thenReturn(Optional.of(sampleSpec));

        ProductSpecificationResponse result = productSpecificationService.getSpecificationById(10L);

        assertNotNull(result);
        assertEquals(10L, result.id());
        assertEquals("CPU", result.specName());
        assertEquals("Intel Core i7-13620H", result.specValue());
        assertEquals(1, result.displayOrder());
        verify(productSpecificationRepository).findById(10L);
    }

    @Test
    void getSpecificationById_NotFound_ThrowsException() {
        when(productSpecificationRepository.findById(99L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> productSpecificationService.getSpecificationById(99L));

        assertEquals("Product specification not found with id: 99", ex.getMessage());
    }

    @Test
    void createSpecification_Success() {
        ProductSpecificationRequest request = new ProductSpecificationRequest("RAM", "16GB DDR5", 2);

        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));
        when(productSpecificationRepository.existsByProductIdAndSpecNameIgnoreCase(1L, "RAM")).thenReturn(false);
        when(productSpecificationRepository.save(any(ProductSpecificationEntity.class))).thenAnswer(inv -> {
            ProductSpecificationEntity entity = inv.getArgument(0);
            entity.setId(11L);
            return entity;
        });

        ProductSpecificationResponse result = productSpecificationService.createSpecification(1L, request);

        assertNotNull(result);
        assertEquals(11L, result.id());
        assertEquals("RAM", result.specName());
        assertEquals("16GB DDR5", result.specValue());
        assertEquals(2, result.displayOrder());
        assertEquals(1L, result.productId());
        verify(productSpecificationRepository).save(any(ProductSpecificationEntity.class));
    }

    @Test
    void createSpecification_ProductNotFound_ThrowsException() {
        ProductSpecificationRequest request = new ProductSpecificationRequest("RAM", "16GB DDR5", 2);
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> productSpecificationService.createSpecification(99L, request));

        assertEquals("Product not found with id: 99", ex.getMessage());
    }

    @Test
    void createSpecification_DuplicateSpecName_ThrowsException() {
        ProductSpecificationRequest request = new ProductSpecificationRequest("CPU", "Intel Core i7", 1);
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));
        when(productSpecificationRepository.existsByProductIdAndSpecNameIgnoreCase(1L, "CPU")).thenReturn(true);

        DuplicateResourceException ex = assertThrows(DuplicateResourceException.class,
                () -> productSpecificationService.createSpecification(1L, request));

        assertEquals("Specification 'CPU' already exists for product with id: 1", ex.getMessage());
    }

    @Test
    void updateSpecification_Success() {
        ProductSpecificationRequest request = new ProductSpecificationRequest("Processor", "Intel Core i7-13700H", 1);

        when(productSpecificationRepository.findById(10L)).thenReturn(Optional.of(sampleSpec));
        when(productSpecificationRepository.existsByProductIdAndSpecNameIgnoreCaseAndIdNot(1L, "Processor", 10L)).thenReturn(false);
        when(productSpecificationRepository.save(any(ProductSpecificationEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductSpecificationResponse result = productSpecificationService.updateSpecification(10L, request);

        assertNotNull(result);
        assertEquals(10L, result.id());
        assertEquals("Processor", result.specName());
        assertEquals("Intel Core i7-13700H", result.specValue());
        verify(productSpecificationRepository).save(sampleSpec);
    }

    @Test
    void updateSpecification_NotFound_ThrowsException() {
        ProductSpecificationRequest request = new ProductSpecificationRequest("Processor", "Intel Core i7-13700H", 1);
        when(productSpecificationRepository.findById(99L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> productSpecificationService.updateSpecification(99L, request));

        assertEquals("Product specification not found with id: 99", ex.getMessage());
    }

    @Test
    void updateSpecification_DuplicateSpecName_ThrowsException() {
        ProductSpecificationRequest request = new ProductSpecificationRequest("RAM", "16GB DDR5", 2);

        when(productSpecificationRepository.findById(10L)).thenReturn(Optional.of(sampleSpec));
        when(productSpecificationRepository.existsByProductIdAndSpecNameIgnoreCaseAndIdNot(1L, "RAM", 10L)).thenReturn(true);

        DuplicateResourceException ex = assertThrows(DuplicateResourceException.class,
                () -> productSpecificationService.updateSpecification(10L, request));

        assertEquals("Specification 'RAM' already exists for product with id: 1", ex.getMessage());
    }

    @Test
    void deleteSpecification_Success() {
        when(productSpecificationRepository.existsById(10L)).thenReturn(true);

        productSpecificationService.deleteSpecification(10L);

        verify(productSpecificationRepository).deleteById(10L);
    }

    @Test
    void deleteSpecification_NotFound_ThrowsException() {
        when(productSpecificationRepository.existsById(99L)).thenReturn(false);

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> productSpecificationService.deleteSpecification(99L));

        assertEquals("Product specification not found with id: 99", ex.getMessage());
    }
}
