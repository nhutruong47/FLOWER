package com.flowerchain.catalog.web;

import com.flowerchain.catalog.application.CatalogService;
import com.flowerchain.catalog.application.CatalogService.AdminProductDto;
import com.flowerchain.catalog.application.CatalogService.BranchPriceDto;
import com.flowerchain.catalog.application.CatalogService.CategoryDto;
import com.flowerchain.shared.api.BranchAuthorizationGuard;
import com.flowerchain.shared.api.FlowerChainPrincipal;
import com.flowerchain.shared.api.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CatalogController {

    private final CatalogService catalogService;
    private final BranchAuthorizationGuard branchGuard;

    public CatalogController(CatalogService catalogService, BranchAuthorizationGuard branchGuard) {
        this.catalogService = catalogService;
        this.branchGuard = branchGuard;
    }

    public record CreateCategoryRequest(
            @NotBlank @Size(min = 2, max = 64) String slug,
            @NotBlank @Size(min = 2, max = 120) String name,
            @Size(max = 500) String description,
            int displayOrder) {}

    public record UpsertProductRequest(
            @NotNull UUID categoryId,
            @NotBlank @Size(min = 2, max = 64) String sku,
            @NotBlank @Size(min = 2, max = 200) String name,
            @Size(max = 1000) String description,
            @Size(max = 500) String imageUrl,
            @NotBlank @Size(max = 32) String unitLabel,
            @NotBlank @Pattern(regexp = "ACTIVE|ARCHIVED") String status,
            Instant availableFrom,
            Instant availableUntil) {}

    public record UpsertBranchPriceRequest(
            @NotBlank @Pattern(regexp = "^[0-9]+(\\.[0-9]{1,2})?$") String unitPrice,
            @NotNull Boolean isSellable) {}

    @GetMapping("/api/v1/storefront/categories")
    public List<CategoryDto> listPublicCategories() {
        return catalogService.listActiveCategories();
    }

    @PostMapping("/api/v1/admin/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryDto createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        branchGuard.requireGlobalHeadOfficeAccess();
        return catalogService.createCategory(
                request.slug(), request.name(), request.description(), request.displayOrder());
    }

    @GetMapping("/api/v1/admin/products")
    public PageResponse<AdminProductDto> listAdminProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        branchGuard.requireStaffPrincipal();
        return catalogService.listAdminProducts(page, size);
    }

    @PostMapping("/api/v1/admin/products")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminProductDto createProduct(@Valid @RequestBody UpsertProductRequest request) {
        branchGuard.requireGlobalHeadOfficeAccess();
        return catalogService.createProduct(
                request.categoryId(),
                request.sku(),
                request.name(),
                request.description(),
                request.imageUrl(),
                request.unitLabel(),
                request.status(),
                request.availableFrom(),
                request.availableUntil());
    }

    @PutMapping("/api/v1/admin/branches/{branchId}/prices/{productId}")
    public BranchPriceDto upsertBranchPrice(
            @PathVariable UUID branchId,
            @PathVariable UUID productId,
            @Valid @RequestBody UpsertBranchPriceRequest request) {
        FlowerChainPrincipal principal = branchGuard.assertCanAccessBranch(branchId);
        branchGuard.requireAnyRole("SYSTEM_OWNER", "HEAD_OFFICE_ADMIN", "BRANCH_MANAGER");
        return catalogService.upsertBranchPrice(
                branchId,
                productId,
                new BigDecimal(request.unitPrice()),
                request.isSellable(),
                principal.actorId());
    }
}
