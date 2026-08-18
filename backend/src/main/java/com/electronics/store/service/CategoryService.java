package com.electronics.store.service;

import com.electronics.store.dto.request.CategoryRequest;
import com.electronics.store.dto.response.CategoryResponse;

import java.util.List;

/**
 * Service interface for Category operations.
 */
public interface CategoryService {

    List<CategoryResponse> getAllCategories();

    CategoryResponse getCategoryById(Long id);

    CategoryResponse createCategory(CategoryRequest request);

    CategoryResponse updateCategory(Long id, CategoryRequest request);

    void deleteCategory(Long id);
}
