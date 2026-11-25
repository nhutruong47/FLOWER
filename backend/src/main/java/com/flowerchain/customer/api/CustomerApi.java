package com.flowerchain.customer.api;

import java.util.UUID;

public interface CustomerApi {

    CustomerDto getActiveCustomerOrThrow(UUID customerId);
}
