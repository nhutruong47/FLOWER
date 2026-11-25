package com.flowerchain.branch.api;

import java.util.List;
import java.util.UUID;

public interface BranchApi {

    BranchDto getBranchOrThrow(UUID branchId);

    BranchDto getActiveBranchOrThrow(UUID branchId);

    List<BranchDto> listActiveBranches();

    List<BranchDto> findActiveBranchesCoveringLocationOrderedByDistance(double deliveryLatitude, double deliveryLongitude);
}
