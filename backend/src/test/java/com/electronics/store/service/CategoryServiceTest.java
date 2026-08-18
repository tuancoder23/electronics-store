package com.electronics.store.service;

import com.electronics.store.dto.request.CategoryRequest;
import com.electronics.store.dto.response.CategoryResponse;
import com.electronics.store.entity.CategoryEntity;
import com.electronics.store.exception.DuplicateResourceException;
import com.electronics.store.exception.ResourceNotFoundException;
import com.electronics.store.mapper.CategoryMapper;
import com.electronics.store.repository.CategoryRepository;
import com.electronics.store.service.impl.CategoryServiceImpl;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Spy
    private CategoryMapper categoryMapper;

    @InjectMocks
    private CategoryServiceImpl categoryService;

    private CategoryEntity sampleCategory;

    @BeforeEach
    void setUp() {
        sampleCategory = CategoryEntity.builder()
                .id(1L)
                .name("Laptop")
                .slug("laptop")
                .description("Laptop devices")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void getAllCategories_shouldReturnList() {
        when(categoryRepository.findAll()).thenReturn(List.of(sampleCategory));

        List<CategoryResponse> result = categoryService.getAllCategories();

        assertEquals(1, result.size());
        assertEquals("Laptop", result.get(0).name());
        assertEquals("laptop", result.get(0).slug());
    }

    @Test
    void getCategoryById_whenFound_shouldReturnResponse() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(sampleCategory));

        CategoryResponse result = categoryService.getCategoryById(1L);

        assertNotNull(result);
        assertEquals(1L, result.id());
        assertEquals("Laptop", result.name());
    }

    @Test
    void getCategoryById_whenNotFound_shouldThrowResourceNotFoundException() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> categoryService.getCategoryById(99L));
    }

    @Test
    void createCategory_shouldSucceed() {
        CategoryRequest request = new CategoryRequest("Smartphones", "Phones & accessories");

        when(categoryRepository.existsByName("Smartphones")).thenReturn(false);
        when(categoryRepository.existsBySlug("smartphones")).thenReturn(false);
        when(categoryRepository.save(any(CategoryEntity.class))).thenAnswer(invocation -> {
            CategoryEntity entity = invocation.getArgument(0);
            entity.setId(2L);
            entity.setCreatedAt(LocalDateTime.now());
            entity.setUpdatedAt(LocalDateTime.now());
            return entity;
        });

        CategoryResponse result = categoryService.createCategory(request);

        assertNotNull(result);
        assertEquals("Smartphones", result.name());
        assertEquals("smartphones", result.slug());
    }

    @Test
    void createCategory_whenNameExists_shouldThrowDuplicateResourceException() {
        CategoryRequest request = new CategoryRequest("Laptop", "Duplicate name");
        when(categoryRepository.existsByName("Laptop")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> categoryService.createCategory(request));
    }

    @Test
    void createCategory_whenSlugExists_shouldAppendSuffix() {
        CategoryRequest request = new CategoryRequest("Laptop", "Another laptop");

        when(categoryRepository.existsByName("Laptop")).thenReturn(false);
        when(categoryRepository.existsBySlug("laptop")).thenReturn(true);
        when(categoryRepository.existsBySlug("laptop-1")).thenReturn(false);
        when(categoryRepository.save(any(CategoryEntity.class))).thenAnswer(invocation -> {
            CategoryEntity entity = invocation.getArgument(0);
            entity.setId(3L);
            return entity;
        });

        CategoryResponse result = categoryService.createCategory(request);

        assertEquals("laptop-1", result.slug());
    }

    @Test
    void updateCategory_shouldSucceed() {
        CategoryRequest request = new CategoryRequest("Gaming Laptop", "Updated description");

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(sampleCategory));
        when(categoryRepository.existsByNameAndIdNot("Gaming Laptop", 1L)).thenReturn(false);
        when(categoryRepository.existsBySlugAndIdNot("gaming-laptop", 1L)).thenReturn(false);
        when(categoryRepository.save(any(CategoryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CategoryResponse result = categoryService.updateCategory(1L, request);

        assertEquals("Gaming Laptop", result.name());
        assertEquals("gaming-laptop", result.slug());
        assertEquals("Updated description", result.description());
    }

    @Test
    void deleteCategory_whenFound_shouldDelete() {
        when(categoryRepository.existsById(1L)).thenReturn(true);

        categoryService.deleteCategory(1L);

        verify(categoryRepository).deleteById(1L);
    }

    @Test
    void deleteCategory_whenNotFound_shouldThrow() {
        when(categoryRepository.existsById(99L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> categoryService.deleteCategory(99L));
    }
}
