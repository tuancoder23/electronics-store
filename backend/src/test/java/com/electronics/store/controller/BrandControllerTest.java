package com.electronics.store.controller;

import com.electronics.store.dto.request.BrandRequest;
import com.electronics.store.dto.response.BrandResponse;
import com.electronics.store.exception.DuplicateResourceException;
import com.electronics.store.exception.GlobalExceptionHandler;
import com.electronics.store.exception.ResourceNotFoundException;
import com.electronics.store.service.BrandService;
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

@WebMvcTest(controllers = {BrandController.class, HealthController.class})
@Import(GlobalExceptionHandler.class)
class BrandControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private BrandService brandService;

    @Test
    void getAllBrands_shouldReturnOk() throws Exception {
        BrandResponse brand = new BrandResponse(1L, "ASUS", "asus", "ASUS laptops", "https://example.com/asus.png", LocalDateTime.now(), LocalDateTime.now());
        when(brandService.getAllBrands()).thenReturn(List.of(brand));

        mockMvc.perform(get("/api/brands"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].name").value("ASUS"))
                .andExpect(jsonPath("$.data[0].slug").value("asus"))
                .andExpect(jsonPath("$.data[0].logoUrl").value("https://example.com/asus.png"));
    }

    @Test
    void getBrandById_whenFound_shouldReturnOk() throws Exception {
        BrandResponse brand = new BrandResponse(1L, "ASUS", "asus", "ASUS laptops", "https://example.com/asus.png", LocalDateTime.now(), LocalDateTime.now());
        when(brandService.getBrandById(1L)).thenReturn(brand);

        mockMvc.perform(get("/api/brands/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("ASUS"));
    }

    @Test
    void getBrandById_whenNotFound_shouldReturn404() throws Exception {
        when(brandService.getBrandById(99L)).thenThrow(new ResourceNotFoundException("Brand not found with id: 99"));

        mockMvc.perform(get("/api/brands/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Brand not found with id: 99"));
    }

    @Test
    void createBrand_whenValid_shouldReturn201() throws Exception {
        BrandRequest request = new BrandRequest("ASUS", "ASUS laptops and computer products", null);
        BrandResponse response = new BrandResponse(1L, "ASUS", "asus", "ASUS laptops and computer products", null, LocalDateTime.now(), LocalDateTime.now());

        when(brandService.createBrand(any(BrandRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/admin/brands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("ASUS"));
    }

    @Test
    void createBrand_whenBlankName_shouldReturn400() throws Exception {
        BrandRequest request = new BrandRequest("", "Description", null);

        mockMvc.perform(post("/api/admin/brands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void createBrand_whenDuplicateName_shouldReturn409() throws Exception {
        BrandRequest request = new BrandRequest("ASUS", "Description", null);
        when(brandService.createBrand(any(BrandRequest.class)))
                .thenThrow(new DuplicateResourceException("Brand name already exists: ASUS"));

        mockMvc.perform(post("/api/admin/brands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Brand name already exists: ASUS"));
    }

    @Test
    void updateBrand_whenValid_shouldReturn200() throws Exception {
        BrandRequest request = new BrandRequest("ASUS ROG", "Republic of Gamers", "https://example.com/rog.png");
        BrandResponse response = new BrandResponse(1L, "ASUS ROG", "asus-rog", "Republic of Gamers", "https://example.com/rog.png", LocalDateTime.now(), LocalDateTime.now());

        when(brandService.updateBrand(eq(1L), any(BrandRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/admin/brands/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("ASUS ROG"))
                .andExpect(jsonPath("$.data.logoUrl").value("https://example.com/rog.png"));
    }

    @Test
    void deleteBrand_shouldReturn200() throws Exception {
        doNothing().when(brandService).deleteBrand(1L);

        mockMvc.perform(delete("/api/admin/brands/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void deleteBrand_whenNotFound_shouldReturn404() throws Exception {
        doThrow(new ResourceNotFoundException("Brand not found with id: 99")).when(brandService).deleteBrand(99L);

        mockMvc.perform(delete("/api/admin/brands/99"))
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
