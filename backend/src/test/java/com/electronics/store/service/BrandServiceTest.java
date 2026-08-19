package com.electronics.store.service;

import com.electronics.store.dto.request.BrandRequest;
import com.electronics.store.dto.response.BrandResponse;
import com.electronics.store.entity.BrandEntity;
import com.electronics.store.exception.DuplicateResourceException;
import com.electronics.store.exception.ResourceNotFoundException;
import com.electronics.store.mapper.BrandMapper;
import com.electronics.store.repository.BrandRepository;
import com.electronics.store.service.impl.BrandServiceImpl;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrandServiceTest {

    @Mock
    private BrandRepository brandRepository;

    @Spy
    private BrandMapper brandMapper;

    @InjectMocks
    private BrandServiceImpl brandService;

    private BrandEntity sampleBrand;

    @BeforeEach
    void setUp() {
        sampleBrand = BrandEntity.builder()
                .id(1L)
                .name("ASUS")
                .slug("asus")
                .description("ASUS laptops and computer products")
                .logoUrl("https://example.com/asus.png")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void getAllBrands_shouldReturnList() {
        when(brandRepository.findAll()).thenReturn(List.of(sampleBrand));

        List<BrandResponse> result = brandService.getAllBrands();

        assertEquals(1, result.size());
        assertEquals("ASUS", result.get(0).name());
        assertEquals("asus", result.get(0).slug());
        assertEquals("https://example.com/asus.png", result.get(0).logoUrl());
    }

    @Test
    void getBrandById_whenFound_shouldReturnResponse() {
        when(brandRepository.findById(1L)).thenReturn(Optional.of(sampleBrand));

        BrandResponse result = brandService.getBrandById(1L);

        assertNotNull(result);
        assertEquals(1L, result.id());
        assertEquals("ASUS", result.name());
    }

    @Test
    void getBrandById_whenNotFound_shouldThrowResourceNotFoundException() {
        when(brandRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> brandService.getBrandById(99L));
    }

    @Test
    void createBrand_shouldSucceed() {
        BrandRequest request = new BrandRequest("Lenovo Legion", "Gaming devices", null);

        when(brandRepository.existsByName("Lenovo Legion")).thenReturn(false);
        when(brandRepository.existsBySlug("lenovo-legion")).thenReturn(false);
        when(brandRepository.save(any(BrandEntity.class))).thenAnswer(invocation -> {
            BrandEntity entity = invocation.getArgument(0);
            entity.setId(2L);
            entity.setCreatedAt(LocalDateTime.now());
            entity.setUpdatedAt(LocalDateTime.now());
            return entity;
        });

        BrandResponse result = brandService.createBrand(request);

        assertNotNull(result);
        assertEquals("Lenovo Legion", result.name());
        assertEquals("lenovo-legion", result.slug());
    }

    @Test
    void createBrand_whenNameExists_shouldThrowDuplicateResourceException() {
        BrandRequest request = new BrandRequest("ASUS", "Duplicate name", null);
        when(brandRepository.existsByName("ASUS")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> brandService.createBrand(request));
    }

    @Test
    void createBrand_whenSlugExists_shouldAppendSuffix() {
        BrandRequest request = new BrandRequest("ASUS", "Another asus", null);

        when(brandRepository.existsByName("ASUS")).thenReturn(false);
        when(brandRepository.existsBySlug("asus")).thenReturn(true);
        when(brandRepository.existsBySlug("asus-1")).thenReturn(false);
        when(brandRepository.save(any(BrandEntity.class))).thenAnswer(invocation -> {
            BrandEntity entity = invocation.getArgument(0);
            entity.setId(3L);
            return entity;
        });

        BrandResponse result = brandService.createBrand(request);

        assertEquals("asus-1", result.slug());
    }

    @Test
    void updateBrand_shouldSucceed() {
        BrandRequest request = new BrandRequest("ASUS ROG", "Republic of Gamers", "https://example.com/rog.png");

        when(brandRepository.findById(1L)).thenReturn(Optional.of(sampleBrand));
        when(brandRepository.existsByNameAndIdNot("ASUS ROG", 1L)).thenReturn(false);
        when(brandRepository.existsBySlugAndIdNot("asus-rog", 1L)).thenReturn(false);
        when(brandRepository.save(any(BrandEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BrandResponse result = brandService.updateBrand(1L, request);

        assertEquals("ASUS ROG", result.name());
        assertEquals("asus-rog", result.slug());
        assertEquals("Republic of Gamers", result.description());
        assertEquals("https://example.com/rog.png", result.logoUrl());
    }

    @Test
    void deleteBrand_whenFound_shouldDelete() {
        when(brandRepository.existsById(1L)).thenReturn(true);

        brandService.deleteBrand(1L);

        verify(brandRepository).deleteById(1L);
    }

    @Test
    void deleteBrand_whenNotFound_shouldThrow() {
        when(brandRepository.existsById(99L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> brandService.deleteBrand(99L));
    }
}
