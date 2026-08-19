package com.electronics.store.service;

import com.electronics.store.dto.request.BrandRequest;
import com.electronics.store.dto.response.BrandResponse;

import java.util.List;

/**
 * Service interface for Brand operations.
 */
public interface BrandService {

    List<BrandResponse> getAllBrands();

    BrandResponse getBrandById(Long id);

    BrandResponse createBrand(BrandRequest request);

    BrandResponse updateBrand(Long id, BrandRequest request);

    void deleteBrand(Long id);
}
