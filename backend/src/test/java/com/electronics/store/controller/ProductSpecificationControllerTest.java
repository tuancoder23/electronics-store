package com.electronics.store.controller;

import com.electronics.store.dto.request.ProductSpecificationRequest;
import com.electronics.store.dto.response.ProductSpecificationResponse;
import com.electronics.store.exception.DuplicateResourceException;
import com.electronics.store.exception.GlobalExceptionHandler;
import com.electronics.store.exception.ResourceNotFoundException;
import com.electronics.store.service.ProductSpecificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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

@WebMvcTest(controllers = {ProductSpecificationController.class})
@Import(GlobalExceptionHandler.class)
class ProductSpecificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProductSpecificationService productSpecificationService;

    private ProductSpecificationResponse buildSampleResponse() {
        return new ProductSpecificationResponse(
                1L,
                1L,
                "CPU",
                "Intel Core i7-13620H",
                1
        );
    }

    @Test
    void getSpecificationsByProductId_ReturnsList() throws Exception {
        when(productSpecificationService.getSpecificationsByProductId(1L))
                .thenReturn(List.of(buildSampleResponse()));

        mockMvc.perform(get("/api/products/1/specifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Specifications retrieved successfully"))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].productId").value(1))
                .andExpect(jsonPath("$.data[0].specName").value("CPU"))
                .andExpect(jsonPath("$.data[0].specValue").value("Intel Core i7-13620H"))
                .andExpect(jsonPath("$.data[0].displayOrder").value(1));
    }

    @Test
    void getSpecificationsByProductId_NotFound_Returns404() throws Exception {
        when(productSpecificationService.getSpecificationsByProductId(99L))
                .thenThrow(new ResourceNotFoundException("Product not found with id: 99"));

        mockMvc.perform(get("/api/products/99/specifications"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Product not found with id: 99"));
    }

    @Test
    void getSpecificationById_ReturnsSingle() throws Exception {
        when(productSpecificationService.getSpecificationById(1L))
                .thenReturn(buildSampleResponse());

        mockMvc.perform(get("/api/product-specifications/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.specName").value("CPU"));
    }

    @Test
    void getSpecificationById_NotFound_Returns404() throws Exception {
        when(productSpecificationService.getSpecificationById(99L))
                .thenThrow(new ResourceNotFoundException("Product specification not found with id: 99"));

        mockMvc.perform(get("/api/product-specifications/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Product specification not found with id: 99"));
    }

    @Test
    void createSpecification_Valid_Returns201() throws Exception {
        ProductSpecificationRequest request = new ProductSpecificationRequest("CPU", "Intel Core i7-13620H", 1);
        when(productSpecificationService.createSpecification(eq(1L), any(ProductSpecificationRequest.class)))
                .thenReturn(buildSampleResponse());

        mockMvc.perform(post("/api/admin/products/1/specifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.specName").value("CPU"));
    }

    @Test
    void createSpecification_ProductNotFound_Returns404() throws Exception {
        ProductSpecificationRequest request = new ProductSpecificationRequest("CPU", "Intel Core i7-13620H", 1);
        when(productSpecificationService.createSpecification(eq(99L), any(ProductSpecificationRequest.class)))
                .thenThrow(new ResourceNotFoundException("Product not found with id: 99"));

        mockMvc.perform(post("/api/admin/products/99/specifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Product not found with id: 99"));
    }

    @Test
    void createSpecification_DuplicateName_Returns409() throws Exception {
        ProductSpecificationRequest request = new ProductSpecificationRequest("CPU", "Intel Core i7-13620H", 1);
        when(productSpecificationService.createSpecification(eq(1L), any(ProductSpecificationRequest.class)))
                .thenThrow(new DuplicateResourceException("Specification 'CPU' already exists for product with id: 1"));

        mockMvc.perform(post("/api/admin/products/1/specifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Specification 'CPU' already exists for product with id: 1"));
    }

    @Test
    void createSpecification_BlankSpecName_Returns400() throws Exception {
        ProductSpecificationRequest request = new ProductSpecificationRequest("", "Intel Core i7-13620H", 1);

        mockMvc.perform(post("/api/admin/products/1/specifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void createSpecification_BlankSpecValue_Returns400() throws Exception {
        ProductSpecificationRequest request = new ProductSpecificationRequest("CPU", "", 1);

        mockMvc.perform(post("/api/admin/products/1/specifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void createSpecification_NegativeDisplayOrder_Returns400() throws Exception {
        ProductSpecificationRequest request = new ProductSpecificationRequest("CPU", "Intel Core i7", -1);

        mockMvc.perform(post("/api/admin/products/1/specifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void updateSpecification_Valid_Returns200() throws Exception {
        ProductSpecificationRequest request = new ProductSpecificationRequest("RAM", "32GB DDR5", 2);
        ProductSpecificationResponse updatedResponse = new ProductSpecificationResponse(1L, 1L, "RAM", "32GB DDR5", 2);

        when(productSpecificationService.updateSpecification(eq(1L), any(ProductSpecificationRequest.class)))
                .thenReturn(updatedResponse);

        mockMvc.perform(put("/api/admin/product-specifications/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.specName").value("RAM"))
                .andExpect(jsonPath("$.data.specValue").value("32GB DDR5"));
    }

    @Test
    void updateSpecification_NotFound_Returns404() throws Exception {
        ProductSpecificationRequest request = new ProductSpecificationRequest("RAM", "32GB DDR5", 2);
        when(productSpecificationService.updateSpecification(eq(99L), any(ProductSpecificationRequest.class)))
                .thenThrow(new ResourceNotFoundException("Product specification not found with id: 99"));

        mockMvc.perform(put("/api/admin/product-specifications/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Product specification not found with id: 99"));
    }

    @Test
    void deleteSpecification_Valid_Returns200() throws Exception {
        doNothing().when(productSpecificationService).deleteSpecification(1L);

        mockMvc.perform(delete("/api/admin/product-specifications/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Specification deleted successfully"));
    }

    @Test
    void deleteSpecification_NotFound_Returns404() throws Exception {
        doThrow(new ResourceNotFoundException("Product specification not found with id: 99"))
                .when(productSpecificationService).deleteSpecification(99L);

        mockMvc.perform(delete("/api/admin/product-specifications/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Product specification not found with id: 99"));
    }
}
