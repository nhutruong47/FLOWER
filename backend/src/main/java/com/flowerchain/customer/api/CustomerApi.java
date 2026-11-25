package com.flowerchain.customer.api;

import java.util.UUID;

public interface CustomerApi {

    CustomerDto getActiveCustomerOrThrow(UUID customerId);

    /** Returns the customer regardless of status, or throws a 404 {@code FlowerChainException}. */
    CustomerDto getCustomerOrThrow(UUID customerId);
}
