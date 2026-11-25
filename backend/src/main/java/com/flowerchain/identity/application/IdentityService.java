package com.flowerchain.identity.application;

import com.flowerchain.audit.api.AuditApi;
import com.flowerchain.audit.api.AuditEntryCommand;
import com.flowerchain.branch.api.BranchApi;
import com.flowerchain.identity.api.StaffUserDto;
import com.flowerchain.identity.internal.StaffBranchAssignmentEntity;
import com.flowerchain.identity.internal.StaffBranchAssignmentRepository;
import com.flowerchain.identity.internal.StaffRefreshSessionEntity;
import com.flowerchain.identity.internal.StaffRefreshSessionRepository;
import com.flowerchain.identity.internal.StaffUserEntity;
import com.flowerchain.identity.internal.StaffUserRepository;
import com.flowerchain.identity.internal.StaffUserRoleEntity;
import com.flowerchain.identity.internal.StaffUserRoleRepository;
import com.flowerchain.shared.api.FlowerChainException;
import com.flowerchain.shared.api.FlowerChainPrincipal;
import com.flowerchain.shared.api.PageResponse;
import com.flowerchain.shared.security.TokenPrincipalResolver;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityService implements TokenPrincipalResolver {

    private static final Set<String> VALID_ROLES = Set.of(
            "SYSTEM_OWNER",
            "HEAD_OFFICE_ADMIN",
            "BRANCH_MANAGER",
            "SALES_STAFF",
            "INVENTORY_STAFF",
            "DELIVERY_STAFF");

    private final StaffUserRepository userRepository;
    private final StaffUserRoleRepository roleRepository;
    private final StaffBranchAssignmentRepository branchAssignmentRepository;
    private final StaffRefreshSessionRepository sessionRepository;
    private final BranchApi branchApi;
    private final AuditApi auditApi;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public IdentityService(
            StaffUserRepository userRepository,
            StaffUserRoleRepository roleRepository,
            StaffBranchAssignmentRepository branchAssignmentRepository,
            StaffRefreshSessionRepository sessionRepository,
            BranchApi branchApi,
            AuditApi auditApi,
            PasswordEncoder passwordEncoder,
            Clock clock) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.branchAssignmentRepository = branchAssignmentRepository;
        this.sessionRepository = sessionRepository;
        this.branchApi = branchApi;
        this.auditApi = auditApi;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    public record StaffAuthResult(String accessToken, int expiresInSeconds, StaffUserDto staff) {}

    @Transactional
    public StaffUserDto createStaff(
            String email,
            String rawPassword,
            String fullName,
            String phone,
            Set<String> roles,
            Set<UUID> authorizedBranchIds,
            UUID actorId) {
        validateRolesAndBranches(roles, authorizedBranchIds);
        String normalizedEmail = email.trim().toLowerCase();
        if (userRepository.findByEmailIgnoreCase(normalizedEmail).isPresent()) {
            throw FlowerChainException.conflict(
                    "DUPLICATE_RESOURCE", "Duplicate staff email", "Staff email '" + normalizedEmail + "' already exists.");
        }
        for (UUID branchId : authorizedBranchIds) {
            branchApi.getBranchOrThrow(branchId);
        }
        Instant now = Instant.now(clock);
        StaffUserEntity saved = userRepository.save(new StaffUserEntity(
                UUID.randomUUID(),
                normalizedEmail,
                passwordEncoder.encode(rawPassword),
                fullName.trim(),
                phone != null ? phone.trim() : null,
                "ACTIVE",
                now));
        saveRolesAndBranches(saved.getId(), roles, authorizedBranchIds, now);
        auditApi.record(new AuditEntryCommand(
                "ROLE_CHANGE",
                "STAFF_CREATED",
                authorizedBranchIds.stream().findFirst().orElse(null),
                "STAFF_USER",
                saved.getId(),
                actorId,
                actorId != null ? "STAFF" : "SYSTEM",
                null,
                "{\"roles\":" + roles + ",\"branches\":" + authorizedBranchIds + "}"));
        return buildDto(saved);
    }

    @Transactional
    public StaffUserDto updateAssignments(
            UUID staffId,
            String status,
            Set<String> roles,
            Set<UUID> authorizedBranchIds,
            UUID actorId) {
        validateRolesAndBranches(roles, authorizedBranchIds);
        StaffUserEntity user = userRepository.findById(staffId)
                .orElseThrow(() -> FlowerChainException.notFound("Staff user " + staffId + " not found."));
        for (UUID branchId : authorizedBranchIds) {
            branchApi.getBranchOrThrow(branchId);
        }
        StaffUserDto previous = buildDto(user);
        Instant now = Instant.now(clock);
        user.updateStatus(status.trim().toUpperCase(), now);
        userRepository.save(user);

        roleRepository.deleteByStaffUserId(staffId);
        branchAssignmentRepository.deleteByStaffUserId(staffId);
        saveRolesAndBranches(staffId, roles, authorizedBranchIds, now);

        if ("DISABLED".equalsIgnoreCase(status)) {
            sessionRepository.findByStaffUserIdAndRevokedAtIsNull(staffId)
                    .forEach(s -> s.revoke(now));
        }

        auditApi.record(new AuditEntryCommand(
                "ROLE_CHANGE",
                "STAFF_ASSIGNMENTS_UPDATED",
                authorizedBranchIds.stream().findFirst().orElse(null),
                "STAFF_USER",
                staffId,
                actorId,
                "STAFF",
                "{\"status\":\"" + previous.status() + "\",\"roles\":" + previous.roles() + ",\"branches\":" + previous.authorizedBranchIds() + "}",
                "{\"status\":\"" + status + "\",\"roles\":" + roles + ",\"branches\":" + authorizedBranchIds + "}"));
        return buildDto(user);
    }

    @Transactional
    public StaffAuthResult login(String email, String rawPassword) {
        String normalizedEmail = email.trim().toLowerCase();
        Optional<StaffUserEntity> found = userRepository.findByEmailIgnoreCase(normalizedEmail);
        Instant now = Instant.now(clock);
        if (found.isEmpty()
                || !"ACTIVE".equalsIgnoreCase(found.get().getStatus())
                || !passwordEncoder.matches(rawPassword, found.get().getPasswordHash())) {
            auditApi.record(new AuditEntryCommand(
                    "LOGIN",
                    "STAFF_LOGIN_FAILURE",
                    null,
                    "STAFF_USER",
                    found.map(StaffUserEntity::getId).orElse(null),
                    null,
                    "STAFF",
                    null,
                    "{\"email\":\"" + normalizedEmail + "\"}"));
            throw FlowerChainException.unauthorized("AUTH_UNAUTHORIZED", "Invalid email or password.");
        }
        StaffUserEntity user = found.get();
        user.recordLogin(now);
        userRepository.save(user);

        String rawToken = "staff_" + UUID.randomUUID();
        sessionRepository.save(new StaffRefreshSessionEntity(
                UUID.randomUUID(),
                user.getId(),
                hashToken(rawToken),
                now.plus(Duration.ofHours(8)),
                now));

        StaffUserDto dto = buildDto(user);
        auditApi.record(new AuditEntryCommand(
                "LOGIN",
                "STAFF_LOGIN_SUCCESS",
                dto.authorizedBranchIds().stream().findFirst().orElse(null),
                "STAFF_USER",
                user.getId(),
                user.getId(),
                "STAFF",
                null,
                null));
        return new StaffAuthResult(rawToken, 28800, dto);
    }

    @Transactional
    public void logout(UUID staffUserId) {
        Instant now = Instant.now(clock);
        sessionRepository.findByStaffUserIdAndRevokedAtIsNull(staffUserId)
                .forEach(s -> s.revoke(now));
    }

    @Transactional(readOnly = true)
    public PageResponse<StaffUserDto> listStaff(Optional<UUID> filterBranchId, int page, int size) {
        List<StaffUserDto> all = userRepository.findAll().stream()
                .map(this::buildDto)
                .filter(dto -> filterBranchId.map(b -> dto.globalAccess() || dto.authorizedBranchIds().contains(b)).orElse(true))
                .toList();
        return PageResponse.of(all, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FlowerChainPrincipal> resolveByToken(String rawToken) {
        if (!rawToken.startsWith("staff_")) {
            return Optional.empty();
        }
        Instant now = Instant.now(clock);
        return sessionRepository.findByTokenHash(hashToken(rawToken))
                .filter(s -> s.getRevokedAt() == null && s.getExpiresAt().isAfter(now))
                .flatMap(s -> userRepository.findById(s.getStaffUserId()))
                .filter(u -> "ACTIVE".equalsIgnoreCase(u.getStatus()))
                .map(u -> {
                    StaffUserDto dto = buildDto(u);
                    return FlowerChainPrincipal.staff(
                            dto.id(), dto.email(), dto.roles(), dto.authorizedBranchIds());
                });
    }

    private void validateRolesAndBranches(Set<String> roles, Set<UUID> authorizedBranchIds) {
        if (roles == null || roles.isEmpty()) {
            throw FlowerChainException.badRequest("ROLES_REQUIRED", "At least one role must be assigned.");
        }
        for (String role : roles) {
            if (!VALID_ROLES.contains(role)) {
                throw FlowerChainException.badRequest("INVALID_ROLE", "Unknown role code: " + role);
            }
        }
        boolean isGlobal = roles.contains("SYSTEM_OWNER") || roles.contains("HEAD_OFFICE_ADMIN");
        if (!isGlobal && (authorizedBranchIds == null || authorizedBranchIds.isEmpty())) {
            throw FlowerChainException.badRequest(
                    "BRANCH_ASSIGNMENT_REQUIRED",
                    "Branch-scoped roles require at least one authorized branchId.");
        }
    }

    private void saveRolesAndBranches(
            UUID staffUserId, Set<String> roles, Set<UUID> authorizedBranchIds, Instant now) {
        for (String role : roles) {
            roleRepository.save(new StaffUserRoleEntity(UUID.randomUUID(), staffUserId, role, now));
        }
        if (authorizedBranchIds != null) {
            for (UUID branchId : authorizedBranchIds) {
                branchAssignmentRepository.save(
                        new StaffBranchAssignmentEntity(UUID.randomUUID(), staffUserId, branchId, now));
            }
        }
    }

    private StaffUserDto buildDto(StaffUserEntity user) {
        Set<String> roles = roleRepository.findByStaffUserId(user.getId()).stream()
                .map(StaffUserRoleEntity::getRoleCode)
                .collect(Collectors.toSet());
        Set<UUID> branchIds = branchAssignmentRepository.findByStaffUserId(user.getId()).stream()
                .map(StaffBranchAssignmentEntity::getBranchId)
                .collect(Collectors.toSet());
        boolean globalAccess = roles.contains("SYSTEM_OWNER") || roles.contains("HEAD_OFFICE_ADMIN");
        return new StaffUserDto(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getStatus(),
                roles,
                globalAccess,
                branchIds);
    }

    private static String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
