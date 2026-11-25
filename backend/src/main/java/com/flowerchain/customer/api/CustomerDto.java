package com.flowerchain.customer.api;

import java.util.UUID;

public record CustomerDto(
        UUID id,
        String email,
        String fullName,
        String phone,
        String status) {}
