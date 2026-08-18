package com.electronics.store.controller;

import com.electronics.store.dto.request.CategoryRequest;
import com.electronics.store.dto.response.CategoryResponse;
import com.electronics.store.exception.DuplicateResourceException;
import com.electronics.store.exception.GlobalExceptionHandler;
import com.electronics.store.exception.ResourceNotFoundException;
import com.electronics.store.service.CategoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {CategoryController.class, HealthController.class})
@Import(GlobalExceptionHandler.class)
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CategoryService categoryService;

    @Test
    void getAllCategories_shouldReturnOk() throws Exception {
        CategoryResponse cat = new CategoryResponse(1L, "Laptop", "laptop", "Description", LocalDateTime.now(), LocalDateTime.now());
        when(categoryService.getAllCategories()).thenReturn(List.of(cat));

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].name").value("Laptop"))
                .andExpect(jsonPath("$.data[0].slug").value("laptop"));
    }

    @Test
    void getCategoryById_whenFound_shouldReturnOk() throws Exception {
        CategoryResponse cat = new CategoryResponse(1L, "Laptop", "laptop", "Description", LocalDateTime.now(), LocalDateTime.now());
        when(categoryService.getCategoryById(1L)).thenReturn(cat);

        mockMvc.perform(get("/api/categories/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("Laptop"));
    }

    @Test
    void getCategoryById_whenNotFound_shouldReturn404() throws Exception {
        when(categoryService.getCategoryById(99L)).thenThrow(new ResourceNotFoundException("Category not found with id: 99"));

        mockMvc.perform(get("/api/categories/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Category not found with id: 99"));
    }

    @Test
    void createCategory_whenValid_shouldReturn201() throws Exception {
        CategoryRequest request = new CategoryRequest("Laptop", "Description");
        CategoryResponse response = new CategoryResponse(1L, "Laptop", "laptop", "Description", LocalDateTime.now(), LocalDateTime.now());

        when(categoryService.createCategory(any(CategoryRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("Laptop"));
    }

    @Test
    void createCategory_whenBlankName_shouldReturn400() throws Exception {
        CategoryRequest request = new CategoryRequest("", "Description");

        mockMvc.perform(post("/api/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void createCategory_whenDuplicateName_shouldReturn409() throws Exception {
        CategoryRequest request = new CategoryRequest("Laptop", "Description");
        when(categoryService.createCategory(any(CategoryRequest.class)))
                .thenThrow(new DuplicateResourceException("Category name already exists: Laptop"));

        mockMvc.perform(post("/api/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Category name already exists: Laptop"));
    }

    @Test
    void updateCategory_whenValid_shouldReturn200() throws Exception {
        CategoryRequest request = new CategoryRequest("Gaming Laptop", "Updated Description");
        CategoryResponse response = new CategoryResponse(1L, "Gaming Laptop", "gaming-laptop", "Updated Description", LocalDateTime.now(), LocalDateTime.now());

        when(categoryService.updateCategory(eq(1L), any(CategoryRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/admin/categories/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Gaming Laptop"));
    }

    @Test
    void deleteCategory_shouldReturn200() throws Exception {
        doNothing().when(categoryService).deleteCategory(1L);

        mockMvc.perform(delete("/api/admin/categories/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void deleteCategory_whenNotFound_shouldReturn404() throws Exception {
        doThrow(new ResourceNotFoundException("Category not found with id: 99")).when(categoryService).deleteCategory(99L);

        mockMvc.perform(delete("/api/admin/categories/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void healthEndpoint_regressionTest_shouldReturnOk() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("UP"));
    }
}
