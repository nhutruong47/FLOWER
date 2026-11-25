package com.flowerchain.catalog.application;

import com.flowerchain.audit.api.AuditApi;
import com.flowerchain.audit.api.AuditEntryCommand;
import com.flowerchain.branch.api.BranchApi;
import com.flowerchain.catalog.api.CatalogApi;
import com.flowerchain.catalog.api.ProductSnapshotForBranch;
import com.flowerchain.catalog.internal.BranchProductPriceEntity;
import com.flowerchain.catalog.internal.BranchProductPriceRepository;
import com.flowerchain.catalog.internal.ProductCategoryEntity;
import com.flowerchain.catalog.internal.ProductCategoryRepository;
import com.flowerchain.catalog.internal.ProductEntity;
import com.flowerchain.catalog.internal.ProductRepository;
import com.flowerchain.shared.api.FlowerChainException;
import com.flowerchain.shared.api.PageResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService implements CatalogApi {

    private final ProductCategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final BranchProductPriceRepository branchPriceRepository;
    private final BranchApi branchApi;
    private final AuditApi auditApi;
    private final Clock clock;

    public CatalogService(
            ProductCategoryRepository categoryRepository,
            ProductRepository productRepository,
            BranchProductPriceRepository branchPriceRepository,
            BranchApi branchApi,
            AuditApi auditApi,
            Clock clock) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.branchPriceRepository = branchPriceRepository;
        this.branchApi = branchApi;
        this.auditApi = auditApi;
        this.clock = clock;
    }

    public record CategoryDto(UUID id, String slug, String name, String description, int displayOrder, String status) {}

    public record AdminProductDto(
            UUID id,
            UUID categoryId,
            String sku,
            String name,
            String description,
            String imageUrl,
            String unitLabel,
            String status,
            Instant availableFrom,
            Instant availableUntil,
            Instant createdAt,
            Instant updatedAt) {}

    public record BranchPriceDto(
            UUID branchId,
            UUID productId,
            String unitPrice,
            String currencyCode,
            boolean isSellable,
            Instant updatedAt) {}

    @Transactional
    public CategoryDto createCategory(String slug, String name, String description, int displayOrder) {
        if (categoryRepository.findBySlugIgnoreCase(slug).isPresent()) {
            throw FlowerChainException.conflict(
                    "DUPLICATE_RESOURCE", "Duplicate category slug", "Category slug '" + slug + "' already exists.");
        }
        Instant now = Instant.now(clock);
        ProductCategoryEntity saved = categoryRepository.save(new ProductCategoryEntity(
                UUID.randomUUID(), slug.trim(), name.trim(), description, displayOrder, "ACTIVE", now));
        return toCategoryDto(saved);
    }

    @Transactional(readOnly = true)
    public List<CategoryDto> listActiveCategories() {
        return categoryRepository.findByStatusOrderByDisplayOrderAsc("ACTIVE").stream()
                .map(this::toCategoryDto)
                .toList();
    }

    @Transactional
    public AdminProductDto createProduct(
            UUID categoryId,
            String sku,
            String name,
            String description,
            String imageUrl,
            String unitLabel,
            String status,
            Instant availableFrom,
            Instant availableUntil) {
        categoryRepository.findById(categoryId)
                .orElseThrow(() -> FlowerChainException.notFound("Category " + categoryId + " not found."));
        if (productRepository.findBySkuIgnoreCase(sku).isPresent()) {
            throw FlowerChainException.conflict(
                    "DUPLICATE_RESOURCE", "Duplicate product SKU", "Product SKU '" + sku + "' already exists.");
        }
        Instant now = Instant.now(clock);
        ProductEntity saved = productRepository.save(new ProductEntity(
                UUID.randomUUID(),
                categoryId,
                sku.trim(),
                name.trim(),
                description,
                imageUrl,
                unitLabel.trim().toUpperCase(),
                status.trim().toUpperCase(),
                availableFrom,
                availableUntil,
                now));
        return toProductDto(saved);
    }

    @Transactional
    public AdminProductDto updateProductDetails(UUID productId, String name, String description, String status) {
        ProductEntity product = productRepository.findById(productId)
                .orElseThrow(() -> FlowerChainException.notFound("Product " + productId + " not found."));
        product.updateDetails(name.trim(), description, status.trim().toUpperCase(), Instant.now(clock));
        return toProductDto(productRepository.save(product));
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminProductDto> listAdminProducts(int page, int size) {
        List<AdminProductDto> items = productRepository.findAll().stream()
                .map(this::toProductDto)
                .toList();
        return PageResponse.of(items, page, size);
    }

    @Transactional
    public BranchPriceDto upsertBranchPrice(
            UUID branchId,
            UUID productId,
            BigDecimal rawPrice,
            boolean isSellable,
            UUID actorStaffId) {
        branchApi.getBranchOrThrow(branchId);
        productRepository.findById(productId)
                .orElseThrow(() -> FlowerChainException.notFound("Product " + productId + " not found."));
        if (rawPrice == null || rawPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw FlowerChainException.badRequest("INVALID_PRICE", "Unit price must be non-negative.");
        }
        BigDecimal scaledPrice = rawPrice.setScale(2, RoundingMode.HALF_UP);
        Instant now = Instant.now(clock);
        Optional<BranchProductPriceEntity> existingOpt =
                branchPriceRepository.findByBranchIdAndProductId(branchId, productId);
        String oldJson = existingOpt
                .map(e -> "{\"unitPrice\":\"" + e.getUnitPrice().toPlainString() + "\",\"isSellable\":" + e.isSellable() + "}")
                .orElse(null);

        BranchProductPriceEntity saved;
        if (existingOpt.isPresent()) {
            BranchProductPriceEntity entity = existingOpt.get();
            entity.updatePrice(scaledPrice, isSellable, actorStaffId, now);
            saved = branchPriceRepository.save(entity);
        } else {
            saved = branchPriceRepository.save(new BranchProductPriceEntity(
                    UUID.randomUUID(),
                    branchId,
                    productId,
                    scaledPrice,
                    "VND",
                    isSellable,
                    actorStaffId,
                    now));
        }

        auditApi.record(new AuditEntryCommand(
                "PRICE_CHANGE",
                "BRANCH_PRICE_UPSERTED",
                branchId,
                "BRANCH_PRODUCT_PRICE",
                saved.getId(),
                actorStaffId,
                actorStaffId != null ? "STAFF" : "SYSTEM",
                oldJson,
                "{\"unitPrice\":\"" + scaledPrice.toPlainString() + "\",\"isSellable\":" + isSellable + "}"));

        return new BranchPriceDto(
                saved.getBranchId(),
                saved.getProductId(),
                saved.getUnitPrice().toPlainString(),
                saved.getCurrencyCode(),
                saved.isSellable(),
                saved.getUpdatedAt());
    }

    @Override
    @Transactional(readOnly = true)
    public ProductSummaryDto getProductSummaryOrThrow(UUID productId) {
        ProductEntity product = productRepository.findById(productId)
                .orElseThrow(() -> FlowerChainException.notFound("Product " + productId + " not found."));
        return new ProductSummaryDto(product.getId(), product.getSku(), product.getName(), product.getStatus());
    }

    @Override
    @Transactional(readOnly = true)
    public ProductSnapshotForBranch getSellableProductSnapshotForBranchOrThrow(UUID branchId, UUID productId) {
        ProductEntity product = productRepository.findById(productId)
                .orElseThrow(() -> FlowerChainException.notFound("Product " + productId + " not found."));
        if (!"ACTIVE".equalsIgnoreCase(product.getStatus())) {
            throw FlowerChainException.conflict(
                    "PRODUCT_NOT_ACTIVE",
                    "Product not active",
                    "Product " + product.getSku() + " is not currently active.");
        }
        BranchProductPriceEntity price = branchPriceRepository
                .findByBranchIdAndProductId(branchId, productId)
                .filter(BranchProductPriceEntity::isSellable)
                .orElseThrow(() -> FlowerChainException.conflict(
                        "BRANCH_PRICE_NOT_CONFIGURED",
                        "Product not sellable at branch",
                        "Product " + product.getSku() + " does not have an active branch price at branch " + branchId + "."));
        return new ProductSnapshotForBranch(
                product.getId(),
                product.getCategoryId(),
                branchId,
                product.getSku(),
                product.getName(),
                product.getDescription(),
                product.getImageUrl(),
                product.getUnitLabel(),
                price.getUnitPrice(),
                price.getCurrencyCode(),
                price.isSellable());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductSnapshotForBranch> listSellableBranchProducts(UUID branchId) {
        branchApi.getActiveBranchOrThrow(branchId);
        return branchPriceRepository.findByBranchIdAndSellableTrue(branchId).stream()
                .map(p -> productRepository.findById(p.getProductId())
                        .filter(prod -> "ACTIVE".equalsIgnoreCase(prod.getStatus()))
                        .map(prod -> new ProductSnapshotForBranch(
                                prod.getId(),
                                prod.getCategoryId(),
                                branchId,
                                prod.getSku(),
                                prod.getName(),
                                prod.getDescription(),
                                prod.getImageUrl(),
                                prod.getUnitLabel(),
                                p.getUnitPrice(),
                                p.getCurrencyCode(),
                                p.isSellable())))
                .flatMap(Optional::stream)
                .toList();
    }

    private CategoryDto toCategoryDto(ProductCategoryEntity e) {
        return new CategoryDto(e.getId(), e.getSlug(), e.getName(), e.getDescription(), e.getDisplayOrder(), e.getStatus());
    }

    private AdminProductDto toProductDto(ProductEntity e) {
        return new AdminProductDto(
                e.getId(),
                e.getCategoryId(),
                e.getSku(),
                e.getName(),
                e.getDescription(),
                e.getImageUrl(),
                e.getUnitLabel(),
                e.getStatus(),
                e.getAvailableFrom(),
                e.getAvailableUntil(),
                e.getCreatedAt(),
                e.getUpdatedAt());
    }
}
