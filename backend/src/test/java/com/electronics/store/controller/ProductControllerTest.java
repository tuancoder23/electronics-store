package com.electronics.store.controller;

import com.electronics.store.dto.request.ProductRequest;
import com.electronics.store.dto.request.ProductSearchCriteria;
import com.electronics.store.dto.response.BrandSummaryResponse;
import com.electronics.store.dto.response.CategorySummaryResponse;
import com.electronics.store.dto.response.PagedResponse;
import com.electronics.store.dto.response.ProductResponse;
import com.electronics.store.entity.ProductStatus;
import com.electronics.store.exception.DuplicateResourceException;
import com.electronics.store.exception.GlobalExceptionHandler;
import com.electronics.store.exception.ResourceNotFoundException;
import com.electronics.store.service.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
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

@WebMvcTest(controllers = {ProductController.class, HealthController.class})
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProductService productService;

    private ProductResponse buildSampleResponse() {
        return new ProductResponse(
                1L,
                "ASUS TUF Gaming F15",
                "asus-tuf-gaming-f15",
                "Gaming laptop",
                new BigDecimal("25990000"),
                new BigDecimal("23990000"),
                10,
                "https://example.com/tuf.png",
                ProductStatus.ACTIVE,
                new CategorySummaryResponse(1L, "Laptop", "laptop"),
                new BrandSummaryResponse(1L, "ASUS", "asus"),
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }

    @Test
    void getAllProducts_shouldReturnOk() throws Exception {
        ProductResponse product = buildSampleResponse();
        when(productService.searchProducts(any(ProductSearchCriteria.class)))
                .thenReturn(new PagedResponse<>(List.of(product), 0, 12, 1, 1, true, true));

        mockMvc.perform(get("/api/products").param("keyword", "ASUS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].id").value(1))
                .andExpect(jsonPath("$.data.content[0].name").value("ASUS TUF Gaming F15"))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(12))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.first").value(true))
                .andExpect(jsonPath("$.data.last").value(true));
    }

    @Test
    void getAllProducts_whenServiceRejectsCriteria_shouldReturn400() throws Exception {
        when(productService.searchProducts(any(ProductSearchCriteria.class)))
                .thenThrow(new IllegalArgumentException("Size must not exceed 100"));

        mockMvc.perform(get("/api/products").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Size must not exceed 100"));
    }

    @Test
    void getAllProducts_whenStatusIsInvalid_shouldReturn400() throws Exception {
        mockMvc.perform(get("/api/products").param("status", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid value for parameter 'status'"));
    }

    @Test
    void getProductById_whenFound_shouldReturnOk() throws Exception {
        ProductResponse product = buildSampleResponse();
        when(productService.getProductById(1L)).thenReturn(product);

        mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("ASUS TUF Gaming F15"));
    }

    @Test
    void getProductById_whenNotFound_shouldReturn404() throws Exception {
        when(productService.getProductById(99L)).thenThrow(new ResourceNotFoundException("Product not found with id: 99"));

        mockMvc.perform(get("/api/products/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Product not found with id: 99"));
    }

    @Test
    void createProduct_whenValid_shouldReturn201() throws Exception {
        ProductRequest request = new ProductRequest(
                "ASUS TUF Gaming F15",
                "Gaming laptop",
                new BigDecimal("25990000"),
                new BigDecimal("23990000"),
                10,
                null,
                ProductStatus.ACTIVE,
                1L,
                1L
        );
        ProductResponse response = buildSampleResponse();

        when(productService.createProduct(any(ProductRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/admin/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("ASUS TUF Gaming F15"));
    }

    @Test
    void createProduct_whenBlankName_shouldReturn400() throws Exception {
        ProductRequest request = new ProductRequest(
                "",
                "Gaming laptop",
                new BigDecimal("25990000"),
                null,
                10,
                null,
                ProductStatus.ACTIVE,
                1L,
                1L
        );

        mockMvc.perform(post("/api/admin/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void createProduct_whenNegativePrice_shouldReturn400() throws Exception {
        ProductRequest request = new ProductRequest(
                "Product",
                "Gaming laptop",
                new BigDecimal("-1000"),
                null,
                10,
                null,
                ProductStatus.ACTIVE,
                1L,
                1L
        );

        mockMvc.perform(post("/api/admin/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void createProduct_whenNegativeQuantity_shouldReturn400() throws Exception {
        ProductRequest request = new ProductRequest(
                "Product",
                "Gaming laptop",
                new BigDecimal("10000"),
                null,
                -5,
                null,
                ProductStatus.ACTIVE,
                1L,
                1L
        );

        mockMvc.perform(post("/api/admin/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void createProduct_whenNullCategory_shouldReturn400() throws Exception {
        ProductRequest request = new ProductRequest(
                "Product",
                "Gaming laptop",
                new BigDecimal("10000"),
                null,
                5,
                null,
                ProductStatus.ACTIVE,
                null,
                1L
        );

        mockMvc.perform(post("/api/admin/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void createProduct_whenDuplicateName_shouldReturn409() throws Exception {
        ProductRequest request = new ProductRequest(
                "ASUS TUF Gaming F15",
                "Gaming laptop",
                new BigDecimal("25990000"),
                null,
                10,
                null,
                ProductStatus.ACTIVE,
                1L,
                1L
        );

        when(productService.createProduct(any(ProductRequest.class)))
                .thenThrow(new DuplicateResourceException("Product name already exists: ASUS TUF Gaming F15"));

        mockMvc.perform(post("/api/admin/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Product name already exists: ASUS TUF Gaming F15"));
    }

    @Test
    void updateProduct_whenValid_shouldReturn200() throws Exception {
        ProductRequest request = new ProductRequest(
                "ASUS TUF Gaming F15 Updated",
                "Updated laptop",
                new BigDecimal("26990000"),
                new BigDecimal("24990000"),
                8,
                "https://example.com/updated.png",
                ProductStatus.ACTIVE,
                1L,
                1L
        );
        ProductResponse response = buildSampleResponse();

        when(productService.updateProduct(eq(1L), any(ProductRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/admin/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void deleteProduct_shouldReturn200() throws Exception {
        doNothing().when(productService).deleteProduct(1L);

        mockMvc.perform(delete("/api/admin/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void deleteProduct_whenNotFound_shouldReturn404() throws Exception {
        doThrow(new ResourceNotFoundException("Product not found with id: 99")).when(productService).deleteProduct(99L);

        mockMvc.perform(delete("/api/admin/products/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }
}
