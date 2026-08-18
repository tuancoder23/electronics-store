package com.electronics.store.repository;

import com.electronics.store.entity.CategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<CategoryEntity, Long> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);

    Optional<CategoryEntity> findBySlug(String slug);

    Optional<CategoryEntity> findByName(String name);
}
