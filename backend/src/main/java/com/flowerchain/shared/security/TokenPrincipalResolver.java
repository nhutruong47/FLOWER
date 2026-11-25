package com.flowerchain.shared.security;

import com.flowerchain.shared.api.FlowerChainPrincipal;
import java.util.Optional;

public interface TokenPrincipalResolver {

    Optional<FlowerChainPrincipal> resolveByToken(String rawToken);
}
