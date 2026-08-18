package com.electronics.store.service.impl;

import com.electronics.store.dto.request.BrandRequest;
import com.electronics.store.dto.response.BrandResponse;
import com.electronics.store.entity.BrandEntity;
import com.electronics.store.exception.DuplicateResourceException;
import com.electronics.store.exception.ResourceNotFoundException;
import com.electronics.store.mapper.BrandMapper;
import com.electronics.store.repository.BrandRepository;
import com.electronics.store.service.BrandService;
import com.electronics.store.util.SlugUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BrandServiceImpl implements BrandService {

    private final BrandRepository brandRepository;
    private final BrandMapper brandMapper;

    @Override
    public List<BrandResponse> getAllBrands() {
        return brandRepository.findAll().stream()
                .map(brandMapper::toResponse)
                .toList();
    }

    @Override
    public BrandResponse getBrandById(Long id) {
        BrandEntity entity = brandRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Brand not found with id: " + id));
        return brandMapper.toResponse(entity);
    }

    @Override
    @Transactional
    public BrandResponse createBrand(BrandRequest request) {
        String trimmedName = request.name().trim();

        if (brandRepository.existsByName(trimmedName)) {
            throw new DuplicateResourceException("Brand name already exists: " + trimmedName);
        }

        String slug = generateUniqueSlug(trimmedName, null);
        BrandEntity entity = brandMapper.toEntity(request, slug);
        BrandEntity saved = brandRepository.save(entity);

        return brandMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public BrandResponse updateBrand(Long id, BrandRequest request) {
        BrandEntity entity = brandRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Brand not found with id: " + id));

        String trimmedName = request.name().trim();

        if (brandRepository.existsByNameAndIdNot(trimmedName, id)) {
            throw new DuplicateResourceException("Brand name already exists: " + trimmedName);
        }

        if (!entity.getName().equals(trimmedName)) {
            entity.setName(trimmedName);
            entity.setSlug(generateUniqueSlug(trimmedName, id));
        }

        entity.setDescription(request.description() != null ? request.description().trim() : null);
        entity.setLogoUrl(request.logoUrl() != null ? request.logoUrl().trim() : null);

        BrandEntity updated = brandRepository.save(entity);
        return brandMapper.toResponse(updated);
    }

    @Override
    @Transactional
    public void deleteBrand(Long id) {
        if (!brandRepository.existsById(id)) {
            throw new ResourceNotFoundException("Brand not found with id: " + id);
        }
        brandRepository.deleteById(id);
    }

    private String generateUniqueSlug(String name, Long currentId) {
        String baseSlug = SlugUtils.toSlug(name);
        String slug = baseSlug;
        int counter = 1;
        while (currentId == null
                ? brandRepository.existsBySlug(slug)
                : brandRepository.existsBySlugAndIdNot(slug, currentId)) {
            slug = baseSlug + "-" + counter;
            counter++;
        }
        return slug;
    }
}
