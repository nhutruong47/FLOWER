# Architecture Diagrams (C4, Module Dependency, Deployment, and Runtime Flows)

- **Status:** PROPOSED (awaiting owner approval at Phase 4 gate before P06)
- **Last updated:** 2026-11-24
- **Owner:** solution-architect · **Task:** TASK-011 (Phase 4 — P05 Solution Architecture)
- **Sources:** `ADR-0001`, `module-map.md`, `cross-cutting-and-security.md`, P04 `functional-requirements.md`.

---

## 1. System Context Diagram (C4 Level 1)

```mermaid
flowchart TB
    Customer["Customer<br/>(Web / Mobile Browser)"]
    BranchStaff["Branch Manager & Staff<br/>(5–30 Flower Shop Branches)"]
    HeadOffice["System Owner & Head Office Admin<br/>(Central Catalog & Chain Oversight)"]

    subgraph FlowerChainSystem["FlowerChain Platform"]
        Platform["FlowerChain E-Commerce &<br/>Multi-Branch Management System"]
    end

    PaymentGateway["External Payment Provider<br/>(Signed Server Webhooks - REQ-009/010)"]
    NotificationGateway["Email / SMS / Notification Gateway<br/>(Pluggable Outbound Adapter)"]

    Customer -->|"Browses branch prices, places delivery or pickup orders"| Platform
    BranchStaff -->|"Manages branch stock, transfers, receipts, spoilage & branch orders"| Platform
    HeadOffice -->|"Manages central catalog, branches, staff roles & chain reports"| Platform

    Platform -->|"Redirects / initiates online payment"| PaymentGateway
    PaymentGateway -->|"Sends signed HTTPS webhook notifications"| Platform
    Platform -->|"Sends transactional order notifications"| NotificationGateway
```

---

## 2. Container Diagram (C4 Level 2)

```mermaid
flowchart TB
    Customer["Customer Browser"]
    Staff["Head Office & Branch Staff Browser"]
    PaymentProvider["Payment Provider"]

    subgraph FlowerChainContainers["FlowerChain Deployment Boundary"]
        Nginx["Nginx Reverse Proxy & Static Server"]
        StorefrontSPA["Customer Storefront SPA<br/>(React + TypeScript + Vite + Tailwind)<br/>frontend/storefront"]
        AdminSPA["Internal Management Admin SPA<br/>(React + TypeScript + Vite + Tailwind)<br/>frontend/admin"]
        SharedPkg["Shared TypeScript Package<br/>(Compile-time DTOs & API Client)<br/>frontend/packages/shared"]
        BackendApp["FlowerChain Backend Application<br/>(Java 21, Spring Boot 3.4 Modular Monolith)<br/>backend/"]
        PostgresDB[("PostgreSQL 16 Database<br/>(Flyway Migrations, ACID Locking & Constraints)")]
    end

    Customer -->|"HTTPS"| Nginx
    Staff -->|"HTTPS"| Nginx
    Nginx -->|"Serves static bundle /"| StorefrontSPA
    Nginx -->|"Serves static bundle /admin"| AdminSPA
    SharedPkg -.->|"Compiled into"| StorefrontSPA
    SharedPkg -.->|"Compiled into"| AdminSPA
    Nginx -->|"Proxies /api/v1/storefront/** & /api/v1/admin/**"| BackendApp
    PaymentProvider -->|"POST /api/v1/webhooks/payments/** (HMAC-signed)"| Nginx
    BackendApp -->|"JDBC / JPA + Flyway (validate)"| PostgresDB
```

---

## 3. Backend Component Diagram (C4 Level 3 — Modular Monolith Internals)

```mermaid
flowchart TB
    subgraph WebLayer["HTTP / Security Perimeter (Spring Security Deny-by-Default)"]
        SecFilter["Security & BranchPrincipal Filter"]
        IdemFilter["Idempotency-Key Filter"]
        ErrHandler["RFC 9457 ProblemDetails Exception Handler"]
        StorefrontApis["Storefront Controllers<br/>(/api/v1/storefront/**)"]
        AdminApis["Admin Controllers<br/>(/api/v1/admin/**)"]
        WebhookApis["Payment Webhook Controller<br/>(/api/v1/webhooks/payments/**)"]
    end

    subgraph SharedKernel["Shared Technical Kernel (com.flowerchain.shared)"]
        BranchGuard["BranchAuthorizationGuard<br/>(REQ-011 / REQ-012)"]
    end

    subgraph BusinessModules["Spring Modulith Business Modules (com.flowerchain.*)"]
        IdentityMod["identity<br/>(Staff, RBAC, Branch Scope)"]
        BranchMod["branch<br/>(5–30 Branches, Zones, Settings)"]
        CustomerMod["customer<br/>(Profiles, Addresses)"]
        CatalogMod["catalog<br/>(Central SKUs, Branch Prices)"]
        InventoryMod["inventory<br/>(Branch Stock, Audit Ledger, Transfers, Receipts, Spoilage)"]
        OrderMod["order<br/>(Allocation, Snapshots, Status Lifecycle)"]
        DeliveryMod["delivery<br/>(Delivery Dispatch & Pickup Hand-over)"]
        PaymentMod["payment<br/>(Webhook Verification, Idempotency, Refunds)"]
        NotificationMod["notification<br/>(Event Listeners & Alerts)"]
        ReportingMod["reporting<br/>(Read-Only Aggregations)"]
        AuditMod["audit<br/>(Immutable Security/Price/Order/Refund Logs)"]
    end

    SecFilter --> StorefrontApis
    SecFilter --> AdminApis
    WebhookApis --> PaymentMod
    StorefrontApis --> OrderMod
    StorefrontApis --> CatalogMod
    StorefrontApis --> BranchMod
    StorefrontApis --> CustomerMod
    AdminApis --> BranchGuard
    BranchGuard --> IdentityMod
    BranchGuard --> BranchMod
    BranchGuard --> CatalogMod
    BranchGuard --> InventoryMod
    BranchGuard --> OrderMod
    BranchGuard --> DeliveryMod
    BranchGuard --> PaymentMod
    BranchGuard --> ReportingMod
    BranchGuard --> AuditMod
```

---

## 4. Module Dependency Diagram (Acyclic Topological DAG)

Arrows point from a dependent module to the module's public `.api` package it calls (`A --> B` means `A` calls `B.api`).

```mermaid
flowchart TD
    Reporting["L7: reporting"]
    Notification["L7: notification (Event Consumer)"]
    Payment["L6: payment"]
    Delivery["L6: delivery"]
    Order["L5: order"]
    Inventory["L4: inventory"]
    Identity["L3: identity"]
    Catalog["L3: catalog"]
    Promotion["L3: promotion (conditional B6)"]
    Branch["L2: branch"]
    Customer["L2: customer"]
    Audit["L1: audit"]
    Shared["L0: shared"]

    Reporting --> Order
    Reporting --> Inventory
    Reporting --> Payment
    Reporting --> Catalog
    Reporting --> Branch
    Reporting --> Audit

    Notification -.->|"Subscribes to domain events"| Order
    Notification -.->|"Subscribes to domain events"| Payment

    Payment --> Order
    Payment --> Audit

    Delivery --> Order
    Delivery --> Branch
    Delivery --> Audit

    Order --> Inventory
    Order --> Catalog
    Order --> Branch
    Order --> Customer
    Order -.->|"If enabled"| Promotion
    Order --> Audit

    Inventory --> Catalog
    Inventory --> Branch
    Inventory --> Audit

    Identity --> Branch
    Identity --> Audit

    Catalog --> Branch
    Catalog --> Audit

    Promotion --> Branch
    Promotion --> Audit

    Branch --> Audit
    Customer --> Audit
    Audit --> Shared
```

---

## 5. Deployment & CI/CD Diagram (`DEC-007`, `DEC-015`)

```mermaid
flowchart LR
    subgraph DevEnv["Developer Workstation (No Local Docker - DEC-015)"]
        DevCode["Git Working Tree<br/>JDK 21 + Node.js LTS (DEC-014)"]
        UnitTests["Local Fast Feedback:<br/>• ./mvnw test (JUnit 5, Mockito, ArchUnit/Modulith)<br/>• npm run build & vitest"]
    end

    subgraph GitHubActions["GitHub Actions CI/CD (Linux Runner with Docker)"]
        CIBuild["1. Build & Format Check"]
        CITestcontainers["2. Integration Tests:<br/>Testcontainers PostgreSQL 16<br/>(Flyway migrations, Concurrency REQ-007,<br/>Audit REQ-008, Webhooks REQ-009/010,<br/>Branch Auth REQ-011/012)"]
        CIPlaywright["3. Playwright E2E Suite"]
        CIDockerBuild["4. Multi-Stage Docker Image Build<br/>(backend, storefront, admin)"]
    end

    subgraph RuntimeEnv["Staging / Production Environment (Approval Gate)"]
        NginxProxy["Nginx Container<br/>(TLS, Static SPAs, /api/v1 Proxy)"]
        SpringContainer["Spring Boot 3.4 Container<br/>(Non-root JVM 21)"]
        ManagedPostgres[("PostgreSQL 16<br/>(Automated Backups & WAL)")]
    end

    DevCode --> UnitTests
    UnitTests -->|"git push (with owner approval)"| CIBuild
    CIBuild --> CITestcontainers
    CITestcontainers --> CIPlaywright
    CIPlaywright --> CIDockerBuild
    CIDockerBuild -->|"Explicit Owner Deployment Approval"| NginxProxy
    NginxProxy --> SpringContainer
    SpringContainer --> ManagedPostgres
```

---

## 6. Runtime Flow 1: Authentication and Branch-Scoped Authorization Flow (`REQ-011`, `REQ-012`)

```mermaid
sequenceDiagram
    autonumber
    actor Staff as Branch Staff (Assigned to Branch A)
    participant AdminUI as Admin App
    participant SecFilter as Spring Security Filter
    participant Identity as Identity Module
    participant Guard as BranchAuthorizationGuard
    participant InvSvc as InventoryService
    participant Audit as Audit Module
    participant DB as PostgreSQL 16

    Staff->>AdminUI: Sign in (email, password)
    AdminUI->>Identity: POST /api/v1/admin/auth/login
    Identity->>DB: Load staff_user + staff_roles + staff_branch_assignments
    Identity->>Identity: Verify bcrypt hash (DelegatingPasswordEncoder)
    Identity->>Audit: Record LOGIN_SUCCESS audit event (UTC)
    Identity-->>AdminUI: 200 OK (Session/Token + Profile {role, branchIds: [A]})

    Note over Staff,DB: Case 1: Authorized Branch A Request
    Staff->>AdminUI: View/Adjust stock for Branch A
    AdminUI->>SecFilter: POST /api/v1/admin/branches/A/inventory/adjustments
    SecFilter->>Identity: Resolve server-side principal (authorizedBranchIds = [A])
    SecFilter->>InvSvc: Invokes controller -> InventoryService
    InvSvc->>Guard: assertCanAccessBranch(Branch A)
    Guard-->>InvSvc: Permitted (A in [A])
    InvSvc->>DB: Update stock + insert inventory_movement in 1 TX
    InvSvc-->>AdminUI: 200 OK

    Note over Staff,DB: Case 2: Cross-Branch Tampering Attempt (REQ-011, REQ-012)
    Staff->>AdminUI: Forged request with branchId = Branch B
    AdminUI->>SecFilter: POST /api/v1/admin/branches/B/inventory/adjustments
    SecFilter->>Identity: Resolve server-side principal (authorizedBranchIds = [A])
    SecFilter->>InvSvc: Invokes controller -> InventoryService
    InvSvc->>Guard: assertCanAccessBranch(Branch B)
    Guard-->>AdminUI: 403 Forbidden (application/problem+json) — Zero DB writes
```

---

## 7. Runtime Flow 2: Online Order Placement, Nearest-Branch Allocation & Snapshot Flow (`REQ-005`..`008`, `REQ-013`, `REQ-014`)

```mermaid
sequenceDiagram
    autonumber
    actor Cust as Customer
    participant StoreUI as Storefront App
    participant OrderSvc as OrderService (order)
    participant BranchApi as BranchApi (branch)
    participant InvApi as InventoryApi (inventory)
    participant CatApi as CatalogApi (catalog)
    participant DB as PostgreSQL 16

    Cust->>StoreUI: Submit Delivery Order (Address, Items, Idempotency-Key)
    StoreUI->>OrderSvc: POST /api/v1/storefront/orders (ignores any client branchId - REQ-012)
    activate OrderSvc
    Note over OrderSvc,DB: Single ACID @Transactional Boundary
    OrderSvc->>BranchApi: findBranchesCoveringLocation(deliveryLat, deliveryLng)
    BranchApi-->>OrderSvc: Candidate branches ordered by distance [Branch A (2km), Branch B (4.5km)]
    OrderSvc->>InvApi: findFirstBranchWithSufficientStock([A, B], itemQuantities)
    Note over InvApi,DB: Branch A lacks stock for SKU-2; Branch B has full stock
    InvApi-->>OrderSvc: Selected processingBranchId = Branch B (REQ-005, REQ-014)
    OrderSvc->>CatApi: getProductSnapshotsForBranch(Branch B, productIds)
    CatApi-->>OrderSvc: Snapshots [{sku, productName, unitPriceAtBranchB}] (REQ-004, REQ-006)
    OrderSvc->>InvApi: reserveStockForOrder(Branch B, orderId, itemQuantities)
    InvApi->>DB: SELECT ... FROM branch_inventories WHERE branch_id=B FOR UPDATE (sorted SKUs)
    InvApi->>DB: Verify available_stock >= qty (REQ-007) & decrement available_stock
    InvApi->>DB: INSERT INTO inventory_movements (-qty, ORDER_RESERVED, branch_id=B) (REQ-008)
    OrderSvc->>DB: INSERT INTO orders (processing_branch_id=B, status=PENDING_PAYMENT)
    OrderSvc->>DB: INSERT INTO order_lines (product_name_snapshot, sku_snapshot, unit_price_snapshot) (REQ-006)
    OrderSvc->>DB: INSERT INTO order_status_history
    OrderSvc-->>StoreUI: 201 Created (orderId, processingBranch=B, snapshottedTotal)
    deactivate OrderSvc
```

---

## 8. Runtime Flow 3: Server-Side Payment Webhook Verification & Idempotency Flow (`REQ-009`, `REQ-010`)

```mermaid
sequenceDiagram
    autonumber
    participant Provider as Payment Provider
    participant WebhookCtrl as PaymentWebhookController
    participant PaySvc as PaymentService (payment)
    participant OrderApi as OrderApi (order)
    participant InvApi as InventoryApi (inventory)
    participant DB as PostgreSQL 16

    Provider->>WebhookCtrl: POST /api/v1/webhooks/payments/{provider} (Raw Body + HMAC Header)
    WebhookCtrl->>PaySvc: verifyAndProcessWebhook(rawBody, signatureHeader)
    PaySvc->>PaySvc: Verify HMAC signature on server (REQ-009)
    alt Invalid or Missing Signature
        PaySvc-->>WebhookCtrl: Reject (401 ProblemDetail) — Zero state changes
    else Valid Signature
        PaySvc->>DB: Check / Insert payment_webhook_events (UNIQUE provider_event_id) (REQ-010)
        alt Duplicate Event ID (Retry Delivery)
            DB-->>PaySvc: Already processed
            PaySvc-->>WebhookCtrl: 200 OK (Idempotent no-op — never processed twice)
        else First Delivery of Event
            PaySvc->>DB: INSERT INTO payments (UNIQUE provider_transaction_id, status=CAPTURED)
            PaySvc->>OrderApi: markOrderPaid(orderId, paymentId)
            OrderApi->>InvApi: confirmOrderStockDeduction(processingBranchId, orderId)
            OrderApi->>DB: UPDATE orders SET status=CONFIRMED + INSERT order_status_history
            PaySvc-->>WebhookCtrl: 200 OK
        end
    end
```

---

## 9. Runtime Flow 4: Inventory Mutation, Concurrency Locking & Inter-Branch Transfer Flow (`REQ-007`, `REQ-008`, `REQ-015`)

```mermaid
sequenceDiagram
    autonumber
    actor Sender as Branch A Staff (Sending Branch)
    actor Receiver as Branch B Staff (Receiving Branch)
    participant InvSvc as InventoryTransferService (inventory)
    participant Guard as BranchAuthorizationGuard
    participant DB as PostgreSQL 16

    Note over Sender,DB: Step 1: Dispatch Transfer from Branch A (-4 units)
    Sender->>InvSvc: POST /api/v1/admin/transfers/{id}/dispatch
    InvSvc->>Guard: assertCanAccessBranch(sourceBranchId = Branch A) (REQ-011, REQ-012)
    InvSvc->>DB: SELECT * FROM branch_inventories WHERE branch_id=A AND product_id=P FOR UPDATE
    alt available_stock < 4
        InvSvc-->>Sender: 409 Conflict (InsufficientStock — stock never < 0, REQ-007)
    else available_stock >= 4
        InvSvc->>DB: UPDATE branch_inventories SET available_stock = available_stock - 4 (CHECK >= 0)
        InvSvc->>DB: INSERT INTO inventory_movements (branch_id=A, delta=-4, reason=TRANSFER_OUT, doc_id=id) (REQ-008)
        InvSvc->>DB: UPDATE stock_transfers SET status=IN_TRANSIT
        InvSvc-->>Sender: 200 OK
    end

    Note over Receiver,DB: Step 2: Confirm Receipt at Branch B (+4 units)
    Receiver->>InvSvc: POST /api/v1/admin/transfers/{id}/receive
    InvSvc->>Guard: assertCanAccessBranch(targetBranchId = Branch B) (REQ-011, REQ-012)
    InvSvc->>DB: SELECT * FROM branch_inventories WHERE branch_id=B AND product_id=P FOR UPDATE
    InvSvc->>DB: UPDATE branch_inventories SET available_stock = available_stock + 4
    InvSvc->>DB: INSERT INTO inventory_movements (branch_id=B, delta=+4, reason=TRANSFER_IN, doc_id=id) (REQ-008)
    InvSvc->>DB: UPDATE stock_transfers SET status=COMPLETED
    InvSvc-->>Receiver: 200 OK
```
