package com.flowerchain.customer.web;

import com.flowerchain.customer.api.CustomerDto;
import com.flowerchain.customer.application.CustomerService;
import com.flowerchain.customer.application.CustomerService.CustomerAuthResult;
import com.flowerchain.shared.api.BranchAuthorizationGuard;
import com.flowerchain.shared.api.FlowerChainPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/storefront")
public class StorefrontCustomerController {

    private final CustomerService customerService;
    private final BranchAuthorizationGuard branchGuard;

    public StorefrontCustomerController(
            CustomerService customerService, BranchAuthorizationGuard branchGuard) {
        this.customerService = customerService;
        this.branchGuard = branchGuard;
    }

    public record CustomerRegisterRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 128) String password,
            @NotBlank @Size(min = 2, max = 150) String fullName,
            @NotBlank @Size(min = 8, max = 32) String phone) {}

    public record CustomerLoginRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 128) String password) {}

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerAuthResult register(@Valid @RequestBody CustomerRegisterRequest request) {
        return customerService.register(
                request.email(), request.password(), request.fullName(), request.phone());
    }

    @PostMapping("/auth/login")
    public CustomerAuthResult login(@Valid @RequestBody CustomerLoginRequest request) {
        return customerService.login(request.email(), request.password());
    }

    @GetMapping("/customers/me")
    public CustomerDto getMyProfile() {
        FlowerChainPrincipal principal = branchGuard.requireCustomerPrincipal();
        return customerService.getActiveCustomerOrThrow(principal.userId());
    }
}
