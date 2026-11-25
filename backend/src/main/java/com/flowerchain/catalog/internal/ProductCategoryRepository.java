package com.flowerchain.catalog.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductCategoryRepository extends JpaRepository<ProductCategoryEntity, UUID> {

    Optional<ProductCategoryEntity> findBySlugIgnoreCase(String slug);

    List<ProductCategoryEntity> findByStatusOrderByDisplayOrderAsc(String status);
}
