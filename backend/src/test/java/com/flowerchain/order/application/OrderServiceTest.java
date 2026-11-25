package com.flowerchain.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.flowerchain.audit.api.AuditApi;
import com.flowerchain.branch.api.BranchApi;
import com.flowerchain.branch.api.BranchDto;
import com.flowerchain.catalog.api.CatalogApi;
import com.flowerchain.catalog.api.ProductSnapshotForBranch;
import com.flowerchain.customer.api.CustomerApi;
import com.flowerchain.customer.api.CustomerDto;
import com.flowerchain.inventory.api.InventoryApi;
import com.flowerchain.order.api.OrderApi.OrderDetailDto;
import com.flowerchain.order.api.OrderApi.OrderLineSnapshotDto;
import com.flowerchain.order.application.OrderService.OrderItemInputDto;
import com.flowerchain.order.internal.OrderEntity;
import com.flowerchain.order.internal.OrderLineEntity;
import com.flowerchain.order.internal.OrderLineRepository;
import com.flowerchain.order.internal.OrderRepository;
import com.flowerchain.order.internal.OrderStatusHistoryRepository;
import com.flowerchain.shared.api.FlowerChainException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

/**
 * Order placement and order reads: active-customer check and sequence-based ORD- numbers (TASK-018 defects 1
 * and 4), snapshot copying (REQ-006, TEST-007 at unit level), branch checks on admin order access (REQ-011,
 * TEST-013) and the revenue-report query (FR-034).
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-25T08:00:00Z");
    private static final UUID BRANCH_A = UUID.randomUUID();
    private static final UUID BRANCH_B = UUID.randomUUID();
    private static final UUID PRODUCT = UUID.randomUUID();
    private static final UUID CUSTOMER = UUID.randomUUID();

    @Mock private OrderRepository orderRepository;
    @Mock private OrderLineRepository orderLineRepository;
    @Mock private OrderStatusHistoryRepository statusHistoryRepository;
    @Mock private BranchApi branchApi;
    @Mock private CustomerApi customerApi;
    @Mock private CatalogApi catalogApi;
    @Mock private InventoryApi inventoryApi;
    @Mock private AuditApi auditApi;
    @Mock private ApplicationEventPublisher eventPublisher;

    @Captor private ArgumentCaptor<List<OrderLineEntity>> linesCaptor;
    @Captor private ArgumentCaptor<OrderEntity> orderCaptor;

    private OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(
                orderRepository,
                orderLineRepository,
                statusHistoryRepository,
                branchApi,
                customerApi,
                catalogApi,
                inventoryApi,
                auditApi,
                eventPublisher,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static BranchDto branch(UUID id, String name) {
        return new BranchDto(id, "BR-" + name, name, "1 Flower St", "District 1", "HCMC", "0280000000",
                new BigDecimal("10.776900"), new BigDecimal("106.700900"), new BigDecimal("15.00"),
                "08:00-20:00", "ACTIVE", NOW, NOW);
    }

    private static ProductSnapshotForBranch snapshot(String sku, String name, String price) {
        return new ProductSnapshotForBranch(PRODUCT, UUID.randomUUID(), BRANCH_A, sku, name, null, null, "BOUQUET",
                new BigDecimal(price), "VND", true);
    }

    private OrderDetailDto placePickupOrder(int quantity) {
        return service.placeOrder(
                CUSTOMER, "PICKUP", BRANCH_A, "PAY_AT_PICKUP", "Nguyen Van A", "0901234567",
                null, null, null, null, null, null, null, null,
                List.of(new OrderItemInputDto(PRODUCT, quantity)));
    }

    @Test
    void disabledCustomerCannotPlaceAnOrder() {
        when(customerApi.getActiveCustomerOrThrow(CUSTOMER))
                .thenThrow(FlowerChainException.forbidden("AUTH_FORBIDDEN_ROLE", "Customer account is disabled."));

        assertThatThrownBy(() -> placePickupOrder(1))
                .isInstanceOfSatisfying(FlowerChainException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(customerApi, never()).getCustomerOrThrow(any());
        verifyNoInteractions(orderRepository, orderLineRepository, inventoryApi, catalogApi, auditApi, eventPublisher);
    }

    @Test
    void placedOrderGetsASequenceNumberAndCopiesSkuNameAndPriceIntoItsLines() {
        when(customerApi.getActiveCustomerOrThrow(CUSTOMER))
                .thenReturn(new CustomerDto(CUSTOMER, "c@example.test", "Customer", "0901234567", "ACTIVE"));
        when(branchApi.getActiveBranchOrThrow(BRANCH_A)).thenReturn(branch(BRANCH_A, "Branch A"));
        when(inventoryApi.hasSufficientStock(BRANCH_A, Map.of(PRODUCT, 2))).thenReturn(true);
        when(catalogApi.getSellableProductSnapshotForBranchOrThrow(BRANCH_A, PRODUCT))
                .thenReturn(snapshot("ROSE-RED-12", "12 Red Roses", "250000.00"));
        when(orderRepository.nextOrderNumber()).thenReturn(2042L);
        when(orderRepository.save(any(OrderEntity.class))).then(returnsFirstArg());
        when(orderLineRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        OrderDetailDto placed = placePickupOrder(2);

        assertThat(placed.orderNumber()).isEqualTo("ORD-2042");
        assertThat(placed.processingBranchId()).isEqualTo(BRANCH_A);
        assertThat(placed.itemsSubtotal()).isEqualTo("500000.00");
        assertThat(placed.lines()).singleElement().satisfies(line -> {
            assertThat(line.skuSnapshot()).isEqualTo("ROSE-RED-12");
            assertThat(line.productNameSnapshot()).isEqualTo("12 Red Roses");
            assertThat(line.unitPriceSnapshot()).isEqualTo("250000.00");
            assertThat(line.lineTotalSnapshot()).isEqualTo("500000.00");
        });
        verify(orderLineRepository).saveAll(linesCaptor.capture());
        assertThat(linesCaptor.getValue()).singleElement().satisfies(line -> {
            assertThat(line.getSkuSnapshot()).isEqualTo("ROSE-RED-12");
            assertThat(line.getProductNameSnapshot()).isEqualTo("12 Red Roses");
            assertThat(line.getUnitPriceSnapshot()).isEqualByComparingTo("250000.00");
            assertThat(line.getQuantity()).isEqualTo(2);
        });
        verify(inventoryApi).reserveStockForOrder(
                eq(BRANCH_A), eq(placed.id()), eq(Map.of(PRODUCT, 2)), eq(CUSTOMER), eq("CUSTOMER"), any());
    }

    @Test
    void readingAnOrderUsesTheStoredSnapshotsAndNeverTheLiveCatalogPrice() {
        when(customerApi.getActiveCustomerOrThrow(CUSTOMER))
                .thenReturn(new CustomerDto(CUSTOMER, "c@example.test", "Customer", "0901234567", "ACTIVE"));
        when(branchApi.getActiveBranchOrThrow(BRANCH_A)).thenReturn(branch(BRANCH_A, "Branch A"));
        when(inventoryApi.hasSufficientStock(BRANCH_A, Map.of(PRODUCT, 1))).thenReturn(true);
        when(catalogApi.getSellableProductSnapshotForBranchOrThrow(BRANCH_A, PRODUCT))
                .thenReturn(snapshot("ROSE-RED-12", "12 Red Roses", "250000.00"));
        when(orderRepository.nextOrderNumber()).thenReturn(2043L);
        when(orderRepository.save(any(OrderEntity.class))).then(returnsFirstArg());
        when(orderLineRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        OrderDetailDto placed = placePickupOrder(1);
        verify(orderRepository).save(orderCaptor.capture());
        verify(orderLineRepository).saveAll(linesCaptor.capture());
        verify(catalogApi).getSellableProductSnapshotForBranchOrThrow(BRANCH_A, PRODUCT);

        // The branch price and product data change after the order was placed.
        when(orderRepository.findById(placed.id())).thenReturn(Optional.of(orderCaptor.getValue()));
        when(orderLineRepository.findByOrderId(placed.id())).thenReturn(linesCaptor.getValue());

        OrderDetailDto reread = service.getCustomerOrderOrThrow404(CUSTOMER, placed.id());

        assertThat(reread.lines()).extracting(OrderLineSnapshotDto::unitPriceSnapshot).containsExactly("250000.00");
        assertThat(reread.lines()).extracting(OrderLineSnapshotDto::skuSnapshot).containsExactly("ROSE-RED-12");
        assertThat(reread.totalAmount()).isEqualTo("250000.00");
        verifyNoMoreInteractions(catalogApi);
    }

    @Test
    void deliveryOrderIgnoresAClientSuppliedBranchAndUsesServerSideAllocation() {
        UUID forgedBranch = UUID.randomUUID();
        when(customerApi.getActiveCustomerOrThrow(CUSTOMER))
                .thenReturn(new CustomerDto(CUSTOMER, "c@example.test", "Customer", "0901234567", "ACTIVE"));
        when(branchApi.findActiveBranchesCoveringLocationOrderedByDistance(10.78, 106.70))
                .thenReturn(List.of(branch(BRANCH_A, "Branch A")));
        when(inventoryApi.findBranchesWithSufficientStock(List.of(BRANCH_A), Map.of(PRODUCT, 1)))
                .thenReturn(List.of(BRANCH_A));
        when(catalogApi.getSellableProductSnapshotForBranchOrThrow(BRANCH_A, PRODUCT))
                .thenReturn(snapshot("ROSE-RED-12", "12 Red Roses", "250000.00"));
        when(orderRepository.nextOrderNumber()).thenReturn(2044L);
        when(orderRepository.save(any(OrderEntity.class))).then(returnsFirstArg());
        when(orderLineRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // TEST-014: the client sends another branch's ID with a DELIVERY order.
        OrderDetailDto placed = service.placeOrder(
                CUSTOMER, "DELIVERY", forgedBranch, "COD", "Nguyen Van A", "0901234567",
                "2 Flower St", "District 3", "Ho Chi Minh City", 10.78, 106.70, null, null, null,
                List.of(new OrderItemInputDto(PRODUCT, 1)));

        assertThat(placed.processingBranchId()).isEqualTo(BRANCH_A);
        verify(branchApi, never()).getActiveBranchOrThrow(forgedBranch);
        verify(inventoryApi).reserveStockForOrder(eq(BRANCH_A), eq(placed.id()), any(), eq(CUSTOMER), eq("CUSTOMER"), any());
    }

    @Test
    void branchStaffCannotReadAnOrderOfAnotherBranch() {
        OrderEntity orderAtB = orderAt(BRANCH_B);
        when(orderRepository.findById(orderAtB.getId())).thenReturn(Optional.of(orderAtB));

        assertThatThrownBy(() -> service.getAdminOrderOrThrow(orderAtB.getId(), false, Set.of(BRANCH_A)))
                .isInstanceOfSatisfying(FlowerChainException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ex.getErrorCode()).isEqualTo("BRANCH_ACCESS_DENIED");
                });
        verifyNoInteractions(orderLineRepository);
    }

    @Test
    void branchStaffCannotChangeTheStatusOfAnOrderOfAnotherBranch() {
        OrderEntity orderAtB = orderAt(BRANCH_B);
        // Staff transitions lock the order row (TASK-017 review F1), so the locking lookup is stubbed.
        when(orderRepository.findByIdForUpdate(orderAtB.getId())).thenReturn(Optional.of(orderAtB));

        assertThatThrownBy(() -> service.transitionOrderStatusByStaff(
                        orderAtB.getId(), "CANCELLED", "forged", UUID.randomUUID(), false, Set.of(BRANCH_A)))
                .isInstanceOfSatisfying(FlowerChainException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThat(orderAtB.getStatus()).isEqualTo("CONFIRMED");
        verifyNoInteractions(inventoryApi, auditApi, eventPublisher);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void revenueReportQueryUsesAHalfOpenRangeAndTheRequestedBranchesOnly() {
        Instant from = Instant.parse("2026-11-01T00:00:00Z");
        Instant to = Instant.parse("2026-12-01T00:00:00Z");
        OrderEntity order = orderAt(BRANCH_A);
        OrderLineEntity line = new OrderLineEntity(UUID.randomUUID(), order.getId(), PRODUCT, "ROSE-RED-12",
                "12 Red Roses", new BigDecimal("250000.00"), 2, new BigDecimal("500000.00"), NOW);
        when(orderRepository.findByProcessingBranchIdInAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
                        Set.of(BRANCH_A), from, to))
                .thenReturn(List.of(order));
        when(orderLineRepository.findByOrderIdIn(List.of(order.getId()))).thenReturn(List.of(line));

        List<OrderDetailDto> orders = service.findOrdersForRevenueReport(Set.of(BRANCH_A), from, to);

        assertThat(orders).singleElement().satisfies(o -> {
            assertThat(o.processingBranchId()).isEqualTo(BRANCH_A);
            assertThat(o.lines()).extracting(OrderLineSnapshotDto::lineTotalSnapshot).containsExactly("500000.00");
        });
    }

    @Test
    void revenueReportQueryWithNoBranchesOrAnEmptyRangeReturnsNothingWithoutQuerying() {
        Instant from = Instant.parse("2026-11-01T00:00:00Z");

        assertThat(service.findOrdersForRevenueReport(Set.of(), from, from.plusSeconds(60))).isEmpty();
        assertThat(service.findOrdersForRevenueReport(Set.of(BRANCH_A), from, from)).isEmpty();
        verifyNoInteractions(orderRepository, orderLineRepository);
        verify(orderLineRepository, never()).findByOrderIdIn(anyCollection());
    }

    private static OrderEntity orderAt(UUID branchId) {
        return new OrderEntity(UUID.randomUUID(), "ORD-9", CUSTOMER, branchId, "PICKUP", "CONFIRMED", "UNPAID", "VND",
                new BigDecimal("500000.00"), BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2),
                new BigDecimal("500000.00"), "Nguyen Van A", "0901234567", null, null, null, null, null, null, null,
                null, NOW);
    }
}
