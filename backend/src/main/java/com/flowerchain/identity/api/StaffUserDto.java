package com.flowerchain.identity.api;

import java.util.Set;
import java.util.UUID;

public record StaffUserDto(
        UUID id,
        String email,
        String fullName,
        String phone,
        String status,
        Set<String> roles,
        boolean globalAccess,
        Set<UUID> authorizedBranchIds) {}
