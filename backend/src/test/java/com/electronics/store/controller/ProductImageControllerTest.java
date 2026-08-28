package com.electronics.store.controller;

import com.electronics.store.dto.request.ProductImageRequest;
import com.electronics.store.dto.response.ProductImageResponse;
import com.electronics.store.exception.GlobalExceptionHandler;
import com.electronics.store.exception.ResourceNotFoundException;
import com.electronics.store.service.ProductImageService;
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

@WebMvcTest(controllers = {ProductImageController.class})
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ProductImageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProductImageService productImageService;

    private ProductImageResponse buildSampleResponse() {
        return new ProductImageResponse(
                1L,
                1L,
                "https://example.com/product-1-front.jpg",
                "Product front view",
                true,
                1,
                LocalDateTime.now()
        );
    }

    @Test
    void getImagesByProductId_ReturnsList() throws Exception {
        when(productImageService.getImagesByProductId(1L))
                .thenReturn(List.of(buildSampleResponse()));

        mockMvc.perform(get("/api/products/1/images"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Product images retrieved successfully"))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].productId").value(1))
                .andExpect(jsonPath("$.data[0].imageUrl").value("https://example.com/product-1-front.jpg"))
                .andExpect(jsonPath("$.data[0].primary").value(true))
                .andExpect(jsonPath("$.data[0].displayOrder").value(1));
    }

    @Test
    void getImagesByProductId_NotFound_Returns404() throws Exception {
        when(productImageService.getImagesByProductId(99L))
                .thenThrow(new ResourceNotFoundException("Product not found with id: 99"));

        mockMvc.perform(get("/api/products/99/images"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Product not found with id: 99"));
    }

    @Test
    void getImageById_ReturnsSingle() throws Exception {
        when(productImageService.getImageById(1L))
                .thenReturn(buildSampleResponse());

        mockMvc.perform(get("/api/product-images/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.imageUrl").value("https://example.com/product-1-front.jpg"));
    }

    @Test
    void getImageById_NotFound_Returns404() throws Exception {
        when(productImageService.getImageById(99L))
                .thenThrow(new ResourceNotFoundException("Product image not found with id: 99"));

        mockMvc.perform(get("/api/product-images/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Product image not found with id: 99"));
    }

    @Test
    void addImage_Valid_Returns201() throws Exception {
        ProductImageRequest request = new ProductImageRequest(
                "https://example.com/product-1-front.jpg",
                "Product front view",
                true,
                1
        );
        when(productImageService.addImage(eq(1L), any(ProductImageRequest.class)))
                .thenReturn(buildSampleResponse());

        mockMvc.perform(post("/api/admin/products/1/images")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.primary").value(true));
    }

    @Test
    void addImage_ProductNotFound_Returns404() throws Exception {
        ProductImageRequest request = new ProductImageRequest(
                "https://example.com/product-1-front.jpg",
                "Product front view",
                true,
                1
        );
        when(productImageService.addImage(eq(99L), any(ProductImageRequest.class)))
                .thenThrow(new ResourceNotFoundException("Product not found with id: 99"));

        mockMvc.perform(post("/api/admin/products/99/images")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Product not found with id: 99"));
    }

    @Test
    void addImage_BlankUrl_Returns400() throws Exception {
        ProductImageRequest request = new ProductImageRequest("", "Front", true, 1);

        mockMvc.perform(post("/api/admin/products/1/images")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void addImage_NegativeDisplayOrder_Returns400() throws Exception {
        ProductImageRequest request = new ProductImageRequest("https://example.com/p.jpg", "Front", true, -1);

        mockMvc.perform(post("/api/admin/products/1/images")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void updateImage_Valid_Returns200() throws Exception {
        ProductImageRequest request = new ProductImageRequest("https://example.com/p2.jpg", "Side", false, 2);
        ProductImageResponse updated = new ProductImageResponse(1L, 1L, "https://example.com/p2.jpg", "Side", false, 2, LocalDateTime.now());

        when(productImageService.updateImage(eq(1L), any(ProductImageRequest.class)))
                .thenReturn(updated);

        mockMvc.perform(put("/api/admin/product-images/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.imageUrl").value("https://example.com/p2.jpg"));
    }

    @Test
    void updateImage_NotFound_Returns404() throws Exception {
        ProductImageRequest request = new ProductImageRequest("https://example.com/p2.jpg", "Side", false, 2);
        when(productImageService.updateImage(eq(99L), any(ProductImageRequest.class)))
                .thenThrow(new ResourceNotFoundException("Product image not found with id: 99"));

        mockMvc.perform(put("/api/admin/product-images/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void setPrimaryImage_Valid_Returns200() throws Exception {
        ProductImageResponse primaryResp = buildSampleResponse();
        when(productImageService.setPrimaryImage(1L)).thenReturn(primaryResp);

        mockMvc.perform(put("/api/admin/product-images/1/primary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.primary").value(true));
    }

    @Test
    void setPrimaryImage_NotFound_Returns404() throws Exception {
        when(productImageService.setPrimaryImage(99L))
                .thenThrow(new ResourceNotFoundException("Product image not found with id: 99"));

        mockMvc.perform(put("/api/admin/product-images/99/primary"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void deleteImage_Valid_Returns200() throws Exception {
        doNothing().when(productImageService).deleteImage(1L);

        mockMvc.perform(delete("/api/admin/product-images/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Product image deleted successfully"));
    }

    @Test
    void deleteImage_NotFound_Returns404() throws Exception {
        doThrow(new ResourceNotFoundException("Product image not found with id: 99"))
                .when(productImageService).deleteImage(99L);

        mockMvc.perform(delete("/api/admin/product-images/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }
}
