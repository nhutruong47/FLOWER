package com.flowerchain.customer.application;

import com.flowerchain.audit.api.AuditApi;
import com.flowerchain.audit.api.AuditEntryCommand;
import com.flowerchain.customer.api.CustomerApi;
import com.flowerchain.customer.api.CustomerDto;
import com.flowerchain.customer.internal.CustomerEntity;
import com.flowerchain.customer.internal.CustomerRepository;
import com.flowerchain.shared.api.FlowerChainException;
import com.flowerchain.shared.api.FlowerChainPrincipal;
import com.flowerchain.shared.security.TokenPrincipalResolver;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService implements CustomerApi, TokenPrincipalResolver {

    private final CustomerRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final AuditApi auditApi;
    private final Clock clock;
    private final Map<String, UUID> activeCustomerTokens = new ConcurrentHashMap<>();

    public CustomerService(
            CustomerRepository repository,
            PasswordEncoder passwordEncoder,
            AuditApi auditApi,
            Clock clock) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.auditApi = auditApi;
        this.clock = clock;
    }

    public record CustomerAuthResult(String accessToken, int expiresInSeconds, CustomerDto customer) {}

    @Transactional
    public CustomerAuthResult register(String email, String rawPassword, String fullName, String phone) {
        String normalizedEmail = email.trim().toLowerCase();
        if (repository.findByEmailIgnoreCase(normalizedEmail).isPresent()) {
            throw FlowerChainException.conflict(
                    "DUPLICATE_RESOURCE",
                    "Email already registered",
                    "Customer email '" + normalizedEmail + "' is already registered.");
        }
        Instant now = Instant.now(clock);
        CustomerEntity saved = repository.save(new CustomerEntity(
                UUID.randomUUID(),
                normalizedEmail,
                passwordEncoder.encode(rawPassword),
                fullName.trim(),
                phone.trim(),
                "ACTIVE",
                now));
        String token = "cust_" + UUID.randomUUID();
        activeCustomerTokens.put(token, saved.getId());
        return new CustomerAuthResult(token, 3600, toDto(saved));
    }

    // noRollbackFor: the failed-login audit entry is written before the 401 exception is thrown and must be
    // committed, not rolled back with it. The failure path writes nothing else.
    @Transactional(noRollbackFor = FlowerChainException.class)
    public CustomerAuthResult login(String email, String rawPassword) {
        String normalizedEmail = email.trim().toLowerCase();
        Optional<CustomerEntity> found = repository.findByEmailIgnoreCase(normalizedEmail);
        String failureReason = loginFailureReason(found, rawPassword);
        if (failureReason != null) {
            // The submitted email and password are never written to the audit log (PII, TASK-018). A known
            // account is identified by its ID; the reason code tells reviewers why the attempt failed.
            auditApi.record(new AuditEntryCommand(
                    "LOGIN",
                    "CUSTOMER_LOGIN_FAILURE",
                    null,
                    "CUSTOMER",
                    found.map(CustomerEntity::getId).orElse(null),
                    null,
                    "CUSTOMER",
                    null,
                    "{\"reason\":\"" + failureReason + "\"}"));
            throw FlowerChainException.unauthorized("AUTH_UNAUTHORIZED", "Invalid email or password.");
        }
        CustomerEntity customer = found.get();
        String token = "cust_" + UUID.randomUUID();
        activeCustomerTokens.put(token, customer.getId());
        auditApi.record(new AuditEntryCommand(
                "LOGIN",
                "CUSTOMER_LOGIN_SUCCESS",
                null,
                "CUSTOMER",
                customer.getId(),
                customer.getId(),
                "CUSTOMER",
                null,
                null));
        return new CustomerAuthResult(token, 3600, toDto(customer));
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerDto getCustomerOrThrow(UUID customerId) {
        return toDto(findCustomerOrThrow(customerId));
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerDto getActiveCustomerOrThrow(UUID customerId) {
        CustomerEntity customer = findCustomerOrThrow(customerId);
        if (!"ACTIVE".equalsIgnoreCase(customer.getStatus())) {
            throw FlowerChainException.forbidden("AUTH_FORBIDDEN_ROLE", "Customer account is disabled.");
        }
        return toDto(customer);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FlowerChainPrincipal> resolveByToken(String rawToken) {
        if (!rawToken.startsWith("cust_")) {
            return Optional.empty();
        }
        UUID customerId = activeCustomerTokens.get(rawToken);
        if (customerId == null) {
            return Optional.empty();
        }
        return repository.findById(customerId)
                .filter(c -> "ACTIVE".equalsIgnoreCase(c.getStatus()))
                .map(c -> FlowerChainPrincipal.customer(c.getId(), c.getEmail()));
    }

    /** Audit reason code for a failed login, or {@code null} when the credentials are valid for an active account. */
    private String loginFailureReason(Optional<CustomerEntity> found, String rawPassword) {
        if (found.isEmpty()) {
            return "UNKNOWN_ACCOUNT";
        }
        if (!"ACTIVE".equalsIgnoreCase(found.get().getStatus())) {
            return "ACCOUNT_NOT_ACTIVE";
        }
        if (!passwordEncoder.matches(rawPassword, found.get().getPasswordHash())) {
            return "INVALID_PASSWORD";
        }
        return null;
    }

    private CustomerEntity findCustomerOrThrow(UUID customerId) {
        return repository.findById(customerId)
                .orElseThrow(() -> FlowerChainException.notFound("Customer " + customerId + " not found."));
    }

    private CustomerDto toDto(CustomerEntity e) {
        return new CustomerDto(e.getId(), e.getEmail(), e.getFullName(), e.getPhone(), e.getStatus());
    }
}
