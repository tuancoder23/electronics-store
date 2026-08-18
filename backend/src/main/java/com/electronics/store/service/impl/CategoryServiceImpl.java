package com.electronics.store.service.impl;

import com.electronics.store.dto.request.CategoryRequest;
import com.electronics.store.dto.response.CategoryResponse;
import com.electronics.store.entity.CategoryEntity;
import com.electronics.store.exception.DuplicateResourceException;
import com.electronics.store.exception.ResourceNotFoundException;
import com.electronics.store.mapper.CategoryMapper;
import com.electronics.store.repository.CategoryRepository;
import com.electronics.store.service.CategoryService;
import com.electronics.store.util.SlugUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    @Override
    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAll().stream()
                .map(categoryMapper::toResponse)
                .toList();
    }

    @Override
    public CategoryResponse getCategoryById(Long id) {
        CategoryEntity entity = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + id));
        return categoryMapper.toResponse(entity);
    }

    @Override
    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        String trimmedName = request.name().trim();

        if (categoryRepository.existsByName(trimmedName)) {
            throw new DuplicateResourceException("Category name already exists: " + trimmedName);
        }

        String slug = generateUniqueSlug(trimmedName, null);
        CategoryEntity entity = categoryMapper.toEntity(request, slug);
        CategoryEntity saved = categoryRepository.save(entity);

        return categoryMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public CategoryResponse updateCategory(Long id, CategoryRequest request) {
        CategoryEntity entity = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + id));

        String trimmedName = request.name().trim();

        if (categoryRepository.existsByNameAndIdNot(trimmedName, id)) {
            throw new DuplicateResourceException("Category name already exists: " + trimmedName);
        }

        if (!entity.getName().equals(trimmedName)) {
            entity.setName(trimmedName);
            entity.setSlug(generateUniqueSlug(trimmedName, id));
        }

        entity.setDescription(request.description() != null ? request.description().trim() : null);

        CategoryEntity updated = categoryRepository.save(entity);
        return categoryMapper.toResponse(updated);
    }

    @Override
    @Transactional
    public void deleteCategory(Long id) {
        if (!categoryRepository.existsById(id)) {
            throw new ResourceNotFoundException("Category not found with id: " + id);
        }
        categoryRepository.deleteById(id);
    }

    private String generateUniqueSlug(String name, Long currentId) {
        String baseSlug = SlugUtils.toSlug(name);
        String slug = baseSlug;
        int counter = 1;
        while (currentId == null
                ? categoryRepository.existsBySlug(slug)
                : categoryRepository.existsBySlugAndIdNot(slug, currentId)) {
            slug = baseSlug + "-" + counter;
            counter++;
        }
        return slug;
    }
}
