# Cross-Cutting Architecture: Multi-Branch Isolation and Security

- **Status:** PROPOSED (awaiting owner approval at Phase 4 gate before P06)
- **Last updated:** 2026-11-24
- **Owner:** solution-architect (reviewed against `security-reviewer` criteria) · **Task:** TASK-011 (Phase 4 — P05 Solution Architecture)
- **Sources:** `REQ-001`..`017`, `CLAUDE.md` §4–8, P04 `roles-and-permissions.md`, `functional-requirements.md`, `non-functional-requirements.md`.

---

## 1. Multi-Branch Data Representation and Isolation Design

### 1.1 Data Classification Across the Chain

Every table in FlowerChain falls into one of four explicit data-ownership categories:

| Category | Tables | `branch_id` Column Rule | Read Scope | Write Scope |
|---|---|---|---|---|
| **1. Central / Chain-Wide Master Data** | `branches`, `system_settings`, `product_categories`, `products`, `staff_users`, `staff_roles` | No `branch_id` (owned by Head Office) | Active branches/catalog readable by Storefront & all Staff; staff list readable by Head Office | `SYSTEM_OWNER`, `HEAD_OFFICE_ADMIN` only (`REQ-003`) |
| **2. Single-Branch Operational Data** | `branch_product_prices`, `branch_inventories`, `inventory_movements`, `goods_receipts`, `stock_adjustments`, `orders` (`processing_branch_id`), `order_deliveries` | **`branch_id NOT NULL`** (or `processing_branch_id NOT NULL` on `orders`) with foreign key to `branches(id)` (`REQ-002`, `REQ-005`) | Head Office: `GLOBAL` (`ALL_BRANCHES`)<br>Branch Staff/Manager: **`WHERE branch_id IN (:authorizedBranchIds)`** (`REQ-011`) | Server verifies target `branch_id` is in caller's `authorizedBranchIds` (`REQ-011`, `REQ-012`) |
| **3. Cross-Branch Operational Data (Dual-Branch)** | `stock_transfers` (`source_branch_id NOT NULL`, `target_branch_id NOT NULL`) | Two non-null branch foreign keys + `CHECK (source_branch_id <> target_branch_id)` (`REQ-015`) | Visible to Head Office and to staff assigned to **either** `source_branch_id` or `target_branch_id` | Dispatch (`-qty`) permitted only for `source_branch_id` scope; Receipt (`+qty`) permitted only for `target_branch_id` scope (`REQ-011`, `REQ-015`) |
| **4. Customer-Scoped Data** | `customers`, `customer_addresses`, customer view of `orders` (`customer_id`) | Customer-owned (`orders` has both `customer_id` and `processing_branch_id NOT NULL`) | Customer sees only rows where `customer_id = authenticatedCustomerId` (`BRULE-027`); returns `404` on mismatch | Customer creates only for self |

### 1.2 Shared Central Catalog vs. Branch-Specific Pricing & Inventory (`REQ-002`, `REQ-003`, `REQ-004`, `REQ-017`)
- **Central Product Identity (`products`):** Managed exclusively by Head Office (`REQ-003`). Defines `id`, `sku` (`UNIQUE`), `name`, `category_id`, `description`, `image_url`, and `status`.
- **Branch Price Matrix (`branch_product_prices`):** Keyed by `UNIQUE (branch_id, product_id)` (`REQ-004`). Holds `unit_price numeric(19,2) NOT NULL CHECK (unit_price >= 0)` and `is_active boolean`.
  - When a branch price is updated, `CatalogService` writes an immutable price-change record to `audit_logs` in the same transaction (`BRULE-019`).
  - When an order is placed (`REQ-006`), `OrderService` fetches `ProductSnapshotForBranch(sku, productName, unitPrice)` from `CatalogApi` for the order's `processingBranchId` and copies those exact values into `order_lines.sku_snapshot`, `order_lines.product_name_snapshot`, and `order_lines.unit_price_snapshot`. Historical orders never join back to `branch_product_prices` for price calculation.
- **Branch Inventory Matrix (`branch_inventories`):** Keyed by `UNIQUE (branch_id, product_id)` (`REQ-002`, `REQ-016`). Holds `available_stock integer NOT NULL CHECK (available_stock >= 0)`, `reserved_stock integer NOT NULL CHECK (reserved_stock >= 0)`, and `version bigint NOT NULL` (optimistic lock column alongside pessimistic `SELECT ... FOR UPDATE` support).

### 1.3 Server-Side Branch Authorization Enforcement (`REQ-011`, `REQ-012`)

Client input (`branchId` in URL path, query string, headers, or JSON request body) is **never** trusted without an explicit server-side check against the authenticated principal (`REQ-012`).

```text
Incoming HTTP Request (/api/v1/admin/...)
  │
  ▼
1. Spring Security Filter Chain authenticates Principal
   → Loads FlowerChainPrincipal { userId, roles, globalAccess: boolean, authorizedBranchIds: Set<UUID> }
   → Determined strictly from server database / verified server session or signed token claims
  │
  ▼
2. Controller passes command/query to @Transactional Application Service
  │
  ▼
3. BranchAuthorizationGuard (in com.flowerchain.shared.security) enforces scope:
   • assertCanAccessBranch(UUID targetBranchId):
       if (!principal.hasGlobalAccess() && !principal.authorizedBranchIds().contains(targetBranchId))
           throw new BranchAccessDeniedException(targetBranchId); // Mapped to 403 / 404 ProblemDetail
   • effectiveBranchFilter(Optional<UUID> requestedBranchId):
       - If requestedBranchId is present: assertCanAccessBranch(requestedBranchId.get()) and return Set.of(requestedBranchId.get())
       - If requestedBranchId is empty and principal.hasGlobalAccess(): return ALL_BRANCHES
       - If requestedBranchId is empty and branch-scoped: return principal.authorizedBranchIds()
  │
  ▼
4. Repository Query executes with mandatory WHERE branch_id IN (:effectiveBranchIds)
```

- **Entity-by-ID Protection (Preventing IDOR across branches):**
  - If a branch user calls `POST /api/v1/admin/orders/{orderId}/status` without passing a `branchId`, `OrderService` loads the order and immediately calls `branchGuard.assertCanAccessBranch(order.getProcessingBranchId())` before reading or mutating the order.
- **Delivery Order Checkout Protection (`REQ-012`, `REQ-014`):**
  - `PlaceDeliveryOrderRequest` does **not** accept an authoritative `branchId` from the customer. Even if a client sends a `branchId` field, the backend allocation engine (`OrderAllocationService`) computes the nearest branch with sufficient stock (`REQ-014`) and assigns that branch as `processingBranchId`.

---

## 2. Security Architecture

### 2.1 Authentication & Session/Token Strategy (`ADR-0005`, resolves `T5`)
- **Dual Route Group Isolation (`CLAUDE.md` §7, §8):**
  - `/api/v1/storefront/**`: Public endpoints (`GET /branches`, `GET /products`) + customer-authenticated endpoints (`/orders/**`, `/profile/**`).
  - `/api/v1/admin/**`: Staff-authenticated endpoints (`SYSTEM_OWNER`, `HEAD_OFFICE_ADMIN`, `BRANCH_MANAGER`, `SALES_STAFF`, `INVENTORY_STAFF`, `DELIVERY_STAFF`).
  - `/api/v1/webhooks/payments/**`: Server-to-server payment provider webhook endpoints authenticated via cryptographic signature verification (`REQ-009`).
- **Recommended Authentication Mechanism (`ADR-0005`):**
  - **Stateless Short-Lived Signed Access Token (JWT) + Server-Revocable Refresh Session (or Spring Session JDBC backed by PostgreSQL):**
  - Every authenticated request resolves the user's current active status, roles, and `authorizedBranchIds` so that deactivating an employee (`FR-004`) or revoking a branch assignment (`FR-005`) takes effect immediately without waiting for long token expiry.
  - If HttpOnly cookies are used for session/refresh transport, Spring Security CSRF protection (`CookieCsrfTokenRepository` / SameSite=`Lax`/`Strict`) and strict CORS origin allowlisting (`CLAUDE.md` §8) are enforced.

### 2.2 Credential Storage & Secret Management (`CLAUDE.md` §8, `NFR-007`)
- **Password Hashing:** `PasswordEncoderFactories.createDelegatingPasswordEncoder()` defaulting to `{bcrypt}` (cost factor `>= 10`).
- **Secrets Management:**
  - Database passwords, JWT signing keys, and payment webhook HMAC secrets are injected exclusively via environment variables (`SPRING_DATASOURCE_PASSWORD`, `FLOWERCHAIN_SECURITY_JWT_SECRET`, `FLOWERCHAIN_PAYMENT_WEBHOOK_SECRET`).
  - No secret ever appears in `application.yml`, committed files, or logs. Only `.env.example` with non-secret placeholder values is committed.

### 2.3 Payment Webhook Security & Idempotency (`REQ-009`, `REQ-010`, `ADR-0007`)
1. **Raw Payload Signature Verification (`REQ-009`):**
   - The webhook filter/controller reads the raw request body bytes and the provider's signature header (e.g., `X-Payment-Signature` + timestamp header to prevent replay attacks).
   - It computes the expected HMAC-SHA256 signature using the server's configured provider secret and compares them in constant time (`MessageDigest.isEqual`).
   - If verification fails, it immediately returns `401 Unauthorized` Problem Details, writes a security audit warning, and touches zero business tables.
2. **Browser Redirect Non-Trust (`CLAUDE.md` §8):**
   - Return URLs loaded by the customer's browser (`GET /checkout/return?orderId=...`) only poll the backend's current order status; they never trigger payment confirmation.
3. **Database-Enforced Idempotency (`REQ-010`):**
   - `payment_webhook_events` has `UNIQUE (provider_name, provider_event_id)` and `payments` has `UNIQUE (provider_name, provider_transaction_id)`.
   - Duplicate webhook deliveries hit the idempotency check / unique constraint, perform zero duplicate order or inventory mutations, and return `200 OK`.

### 2.4 Inventory Concurrency & Locking Strategy (`REQ-007`, `ADR-0006`)
- **Preventing Deadlocks & Negative Stock Under Concurrency:**
  1. When an order or transfer touches multiple SKUs at a branch, the service sorts the product IDs in ascending deterministic order (`ORDER BY product_id ASC`) before acquiring row locks (`SELECT ... FROM branch_inventories WHERE branch_id = :branchId AND product_id IN (:sortedProductIds) FOR UPDATE`). Sorting prevents lock-order deadlocks when two concurrent orders buy overlapping products.
  2. The service checks `available_stock >= requested_qty` for every locked row.
  3. It decrements `available_stock` and inserts the `inventory_movements` audit rows (`REQ-008`) in the same transaction.
  4. As a final defense in depth, PostgreSQL enforces `CONSTRAINT chk_branch_inventories_available_non_negative CHECK (available_stock >= 0)`.

### 2.5 Mandatory 6-Category Audit Trail (`REQ-008`, `CLAUDE.md` §8, `FR-036`)

| Audit Category | Storage Table | Written By | Triggering Events |
|---|---|---|---|
| 1. **Inventory changes (`REQ-008`)** | `inventory_movements` | `inventory` module | Order reservation/deduction, cancellation release, goods receipt, spoilage/damage/adjustment, inter-branch transfer send/receive |
| 2. **Logins** | `audit_logs` | `identity` → `audit` | Staff/customer login success, login failure, logout |
| 3. **Role & branch assignment changes** | `audit_logs` | `identity` → `audit` | Staff creation/deactivation, role change, authorized branch assignment change |
| 4. **Price changes** | `audit_logs` | `catalog` → `audit` | Branch product price created or updated (`old_price` → `new_price`) |
| 5. **Order status & branch changes** | `order_status_history` + `audit_logs` | `order` → `audit` | Order placement, every status transition, cancellation, exception branch reassignment |
| 6. **Refunds** | `payment_refunds` + `audit_logs` | `payment` → `audit` | Refund approved/recorded against a payment |
