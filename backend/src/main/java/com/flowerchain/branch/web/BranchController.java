package com.flowerchain.branch.web;

import com.flowerchain.branch.api.BranchDto;
import com.flowerchain.branch.application.BranchService;
import com.flowerchain.shared.api.BranchAuthorizationGuard;
import com.flowerchain.shared.api.FlowerChainPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BranchController {

    private final BranchService branchService;
    private final BranchAuthorizationGuard branchGuard;

    public BranchController(BranchService branchService, BranchAuthorizationGuard branchGuard) {
        this.branchService = branchService;
        this.branchGuard = branchGuard;
    }

    public record UpsertBranchRequest(
            @NotBlank @Size(min = 2, max = 32) String code,
            @NotBlank @Size(min = 2, max = 150) String name,
            @NotBlank @Size(max = 255) String streetAddress,
            @NotBlank @Size(max = 100) String district,
            @NotBlank @Size(max = 100) String city,
            @NotBlank @Size(max = 32) String phone,
            @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
            @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
            @NotNull @DecimalMin(value = "0.1") BigDecimal maxDeliveryRadiusKm,
            @NotBlank @Size(max = 128) String operatingHours,
            @NotBlank @Pattern(regexp = "ACTIVE|INACTIVE") String status) {}

    public record PublicBranchResponse(
            UUID id,
            String code,
            String name,
            String streetAddress,
            String district,
            String city,
            String phone,
            BigDecimal latitude,
            BigDecimal longitude,
            String operatingHours) {}

    @GetMapping("/api/v1/storefront/branches")
    public List<PublicBranchResponse> listPublicBranches() {
        return branchService.listActiveBranches().stream()
                .map(b -> new PublicBranchResponse(
                        b.id(),
                        b.code(),
                        b.name(),
                        b.streetAddress(),
                        b.district(),
                        b.city(),
                        b.phone(),
                        b.latitude(),
                        b.longitude(),
                        b.operatingHours()))
                .toList();
    }

    @GetMapping("/api/v1/admin/branches")
    public List<BranchDto> listAdminBranches() {
        FlowerChainPrincipal principal = branchGuard.requireStaffPrincipal();
        return branchService.listBranchesForStaff(
                principal.globalAccess(), principal.authorizedBranchIds());
    }

    @PostMapping("/api/v1/admin/branches")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public BranchDto createBranch(@Valid @RequestBody UpsertBranchRequest request) {
        branchGuard.requireGlobalHeadOfficeAccess();
        return branchService.createBranch(
                request.code(),
                request.name(),
                request.streetAddress(),
                request.district(),
                request.city(),
                request.phone(),
                request.latitude(),
                request.longitude(),
                request.maxDeliveryRadiusKm(),
                request.operatingHours(),
                request.status());
    }

    @PutMapping("/api/v1/admin/branches/{branchId}")
    public BranchDto updateBranch(
            @PathVariable UUID branchId, @Valid @RequestBody UpsertBranchRequest request) {
        branchGuard.requireGlobalHeadOfficeAccess();
        return branchService.updateBranch(
                branchId,
                request.code(),
                request.name(),
                request.streetAddress(),
                request.district(),
                request.city(),
                request.phone(),
                request.latitude(),
                request.longitude(),
                request.maxDeliveryRadiusKm(),
                request.operatingHours(),
                request.status());
    }
}
