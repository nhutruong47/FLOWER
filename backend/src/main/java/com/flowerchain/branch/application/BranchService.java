package com.flowerchain.branch.application;

import com.flowerchain.branch.api.BranchApi;
import com.flowerchain.branch.api.BranchDto;
import com.flowerchain.branch.internal.BranchEntity;
import com.flowerchain.branch.internal.BranchRepository;
import com.flowerchain.shared.api.FlowerChainException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BranchService implements BranchApi {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private final BranchRepository repository;
    private final Clock clock;

    public BranchService(BranchRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public BranchDto getBranchOrThrow(UUID branchId) {
        BranchEntity entity = repository.findById(branchId)
                .orElseThrow(() -> FlowerChainException.notFound("Branch " + branchId + " was not found."));
        return toDto(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public BranchDto getActiveBranchOrThrow(UUID branchId) {
        BranchDto branch = getBranchOrThrow(branchId);
        if (!"ACTIVE".equalsIgnoreCase(branch.status())) {
            throw FlowerChainException.conflict(
                    "BRANCH_INACTIVE", "Branch is inactive", "Branch " + branch.name() + " is not currently active.");
        }
        return branch;
    }

    @Override
    @Transactional(readOnly = true)
    public List<BranchDto> listActiveBranches() {
        return repository.findByStatusOrderByCodeAsc("ACTIVE").stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BranchDto> listBranchesForStaff(boolean globalAccess, Set<UUID> authorizedBranchIds) {
        return repository.findAll().stream()
                .filter(b -> globalAccess || authorizedBranchIds.contains(b.getId()))
                .sorted(Comparator.comparing(BranchEntity::getCode))
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BranchDto> findActiveBranchesCoveringLocationOrderedByDistance(
            double deliveryLatitude, double deliveryLongitude) {
        return repository.findByStatusOrderByCodeAsc("ACTIVE").stream()
                .filter(b -> haversineDistanceKm(
                                deliveryLatitude,
                                deliveryLongitude,
                                b.getLatitude().doubleValue(),
                                b.getLongitude().doubleValue())
                        <= b.getMaxDeliveryRadiusKm().doubleValue())
                .sorted(Comparator
                        .comparingDouble((BranchEntity b) -> haversineDistanceKm(
                                deliveryLatitude,
                                deliveryLongitude,
                                b.getLatitude().doubleValue(),
                                b.getLongitude().doubleValue()))
                        .thenComparing(BranchEntity::getCode))
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public BranchDto createBranch(
            String code,
            String name,
            String streetAddress,
            String district,
            String city,
            String phone,
            BigDecimal latitude,
            BigDecimal longitude,
            BigDecimal maxDeliveryRadiusKm,
            String operatingHours,
            String status) {
        if (repository.findByCodeIgnoreCase(code).isPresent()) {
            throw FlowerChainException.conflict(
                    "DUPLICATE_RESOURCE", "Duplicate branch code", "Branch code '" + code + "' already exists.");
        }
        Instant now = Instant.now(clock);
        BranchEntity saved = repository.save(new BranchEntity(
                UUID.randomUUID(),
                code.trim(),
                name.trim(),
                streetAddress.trim(),
                district.trim(),
                city.trim(),
                phone.trim(),
                latitude,
                longitude,
                maxDeliveryRadiusKm,
                operatingHours.trim(),
                status.trim().toUpperCase(),
                now));
        return toDto(saved);
    }

    @Transactional
    public BranchDto updateBranch(
            UUID branchId,
            String code,
            String name,
            String streetAddress,
            String district,
            String city,
            String phone,
            BigDecimal latitude,
            BigDecimal longitude,
            BigDecimal maxDeliveryRadiusKm,
            String operatingHours,
            String status) {
        BranchEntity entity = repository.findById(branchId)
                .orElseThrow(() -> FlowerChainException.notFound("Branch " + branchId + " was not found."));
        repository.findByCodeIgnoreCase(code).ifPresent(other -> {
            if (!other.getId().equals(branchId)) {
                throw FlowerChainException.conflict(
                        "DUPLICATE_RESOURCE", "Duplicate branch code", "Branch code '" + code + "' already exists.");
            }
        });
        entity.update(
                code.trim(),
                name.trim(),
                streetAddress.trim(),
                district.trim(),
                city.trim(),
                phone.trim(),
                latitude,
                longitude,
                maxDeliveryRadiusKm,
                operatingHours.trim(),
                status.trim().toUpperCase(),
                Instant.now(clock));
        return toDto(repository.save(entity));
    }

    public static double haversineDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1))
                        * Math.cos(Math.toRadians(lat2))
                        * Math.sin(dLon / 2)
                        * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    private BranchDto toDto(BranchEntity e) {
        return new BranchDto(
                e.getId(),
                e.getCode(),
                e.getName(),
                e.getStreetAddress(),
                e.getDistrict(),
                e.getCity(),
                e.getPhone(),
                e.getLatitude(),
                e.getLongitude(),
                e.getMaxDeliveryRadiusKm(),
                e.getOperatingHours(),
                e.getStatus(),
                e.getCreatedAt(),
                e.getUpdatedAt());
    }
}
