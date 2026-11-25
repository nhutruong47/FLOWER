package com.flowerchain.identity.web;

import com.flowerchain.identity.api.StaffUserDto;
import com.flowerchain.identity.application.IdentityService;
import com.flowerchain.identity.application.IdentityService.StaffAuthResult;
import com.flowerchain.shared.api.BranchAuthorizationGuard;
import com.flowerchain.shared.api.FlowerChainPrincipal;
import com.flowerchain.shared.api.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminIdentityController {

    private final IdentityService identityService;
    private final BranchAuthorizationGuard branchGuard;

    public AdminIdentityController(
            IdentityService identityService, BranchAuthorizationGuard branchGuard) {
        this.identityService = identityService;
        this.branchGuard = branchGuard;
    }

    public record StaffLoginRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 128) String password) {}

    public record CreateStaffRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 128) String password,
            @NotBlank @Size(min = 2, max = 150) String fullName,
            String phone,
            @NotEmpty Set<String> roles,
            @NotNull Set<UUID> authorizedBranchIds) {}

    public record UpdateStaffAssignmentsRequest(
            @NotBlank @Pattern(regexp = "ACTIVE|DISABLED") String status,
            @NotEmpty Set<String> roles,
            @NotNull Set<UUID> authorizedBranchIds) {}

    @PostMapping("/auth/login")
    public StaffAuthResult login(@Valid @RequestBody StaffLoginRequest request) {
        return identityService.login(request.email(), request.password());
    }

    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout() {
        FlowerChainPrincipal principal = branchGuard.requireStaffPrincipal();
        identityService.logout(principal.userId());
    }

    @GetMapping("/staff")
    public PageResponse<StaffUserDto> listStaff(
            @RequestParam(required = false) UUID branchId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        branchGuard.requireGlobalHeadOfficeAccess();
        return identityService.listStaff(Optional.ofNullable(branchId), page, size);
    }

    @PostMapping("/staff")
    @ResponseStatus(HttpStatus.CREATED)
    public StaffUserDto createStaff(@Valid @RequestBody CreateStaffRequest request) {
        FlowerChainPrincipal principal = branchGuard.requireGlobalHeadOfficeAccess();
        return identityService.createStaff(
                request.email(),
                request.password(),
                request.fullName(),
                request.phone(),
                request.roles(),
                request.authorizedBranchIds(),
                principal.userId());
    }

    @PutMapping("/staff/{staffId}/assignments")
    public StaffUserDto updateAssignments(
            @PathVariable UUID staffId,
            @Valid @RequestBody UpdateStaffAssignmentsRequest request) {
        FlowerChainPrincipal principal = branchGuard.requireGlobalHeadOfficeAccess();
        return identityService.updateAssignments(
                staffId,
                request.status(),
                request.roles(),
                request.authorizedBranchIds(),
                principal.userId());
    }
}
