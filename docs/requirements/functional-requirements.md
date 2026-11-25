# Functional Requirements Specification (`FR-001` to `FR-046`)

- **Status:** DRAFT (awaiting owner approval at Phase 3 gate before P05)
- **Last updated:** 2026-11-24
- **Owner:** business-analyst (`requirements-engineering` skill) · **Task:** TASK-010 (Phase 3 — P04 Requirements Engineering)
- **Sources:**
  - Owner Brief (2026-11-23): Baseline rules `REQ-001` to `REQ-012` (`BRULE-001` to `BRULE-012`)
  - Owner Phase 2 Answers (2026-11-23): `B2`, `B3a`, `B3b`, `B9` (`REQ-013` to `REQ-017` / `BRULE-013` to `BRULE-017`)
  - Approved P03 Business Analysis (`docs/business-analysis/business-overview-and-model.md`, `stakeholders-and-actors.md`, `business-process-maps.md`, `use-case-catalog.md`, `business-rules-catalog.md`)
- **Classification Legend:**
  - **CONFIRMED:** Directly grounded in owner brief or owner answers (`B2`, `B3a`, `B3b`, `B9`).
  - **INFERRED:** Necessary technical or operational capability required to fulfill a confirmed requirement.
  - **PROPOSED:** Recommended MVP capability and acceptance criteria based on P03 analysis; requires owner sign-off.
  - **NEEDS CONFIRMATION:** Conditional upon an open question (`Bx` / `Tx`) in [open-questions.md](open-questions.md).

---

## 1. Authentication and Account Management (`FR-001` to `FR-003`)

### FR-001: Staff Authentication and Session/Token Issuance
- **Classification:** CONFIRMED (baseline security `CLAUDE.md` §8; mechanism `T5` PROPOSED)
- **Priority:** Must Have (MVP)
- **Source:** `CLAUDE.md` §8, `BRULE-028`, `BRULE-029`, `BRULE-030`, `T5`
- **Description & Rationale:** Internal staff (System Owner, Head Office Administrator, Branch Manager, Sales Staff, Inventory Staff, Delivery Staff) must authenticate with valid credentials before accessing any admin endpoint so the server can resolve their role and authorized branch scope (`REQ-011`).
- **Preconditions:** Staff account exists and is active (`ENABLED`), with a password hashed via `DelegatingPasswordEncoder` (`bcrypt` or stronger).
- **Main Flow:**
  1. Staff user submits credentials (email/username and password) to the authentication endpoint.
  2. Backend verifies password hash and account active status.
  3. Backend resolves the user's assigned roles and authorized `branchId` list on the server.
  4. Backend writes a login success entry to the security audit trail (`BRULE-030`) and issues an authenticated session/token (`T5`).
- **Alternative Flows:**
  - *A1 (Explicit Logout):* Authenticated staff user logs out; backend invalidates the session/token and records the logout event.
- **Exception Behavior:**
  - *E1 (Invalid credentials or disabled account):* Backend rejects login with `401 Unauthorized` (RFC 9457 Problem Details), writes a failed login audit entry without logging the raw password, and grants no access.
- **Acceptance Criteria:**
  - **AC1:** Given an active staff user with a valid bcrypt-hashed password, when they submit valid credentials, then the backend returns `200 OK` with an authenticated session/token and records a `LOGIN_SUCCESS` audit event with actor ID and UTC timestamp.
  - **AC2:** Given an invalid password or deactivated account, when login is attempted, then the backend returns `401 Unauthorized` in `application/problem+json` format, records a `LOGIN_FAILURE` audit event, and never logs the plaintext password.
  - **AC3:** Given any unauthenticated request to an `/api/v1/admin/**` endpoint, when received by the server, then Spring Security denies the request by default with `401 Unauthorized`.
- **Dependencies:** None
- **Related Business Rules / Use Cases:** `BRULE-011`, `BRULE-028`, `BRULE-029`, `BRULE-030`, `UC-HO-03`

### FR-002: Customer Authentication and Guest/Account Checkout Identity
- **Classification:** PROPOSED / NEEDS CONFIRMATION (`B6`, `T5`)
- **Priority:** Must Have (MVP)
- **Source:** `CLAUDE.md` §8 ("Customers see only their own orders and data"), `BRULE-027`, `B6`, `T5`
- **Description & Rationale:** Customers using the storefront need to register/sign in (and optionally check out as a guest if approved under `B6`) so they can place orders and view only their own order history (`BRULE-027`).
- **Preconditions:** Customer accesses the public storefront.
- **Main Flow:**
  1. Customer registers an account with email/phone and password (hashed via `DelegatingPasswordEncoder`) or signs in.
  2. Backend authenticates the customer principal and scopes all `/api/v1/storefront/orders/**` queries to that customer's ID.
- **Alternative Flows:**
  - *A1 (Guest checkout — pending `B6`):* If guest checkout is enabled, customer places an order with contact details and receives a secure order lookup reference.
- **Exception Behavior:**
  - *E1 (Cross-customer access attempt):* Authenticated customer A requests customer B's order ID; backend returns `404 Not Found` (`CLAUDE.md` §7) to prevent confirming the record exists.
- **Acceptance Criteria:**
  - **AC1:** Given a registered customer, when they authenticate and list their orders, then only orders owned by their `customerId` are returned.
  - **AC2:** Given authenticated customer A, when they request `/api/v1/storefront/orders/{orderIdOfCustomerB}`, then the backend returns `404 Not Found` and leaks no data.
- **Dependencies:** `FR-001`
- **Related Business Rules / Use Cases:** `BRULE-027`, `BRULE-028`, `BRULE-029`, `UC-CUST-06`

### FR-003: Password Hashing and Credential Security
- **Classification:** CONFIRMED
- **Priority:** Must Have (MVP)
- **Source:** `CLAUDE.md` §5, §8, `BRULE-029`
- **Description & Rationale:** Passwords must be hashed with Spring Security's `DelegatingPasswordEncoder` (`bcrypt` or stronger), and credentials/tokens must never appear in application logs or API responses.
- **Preconditions:** Any user registration, staff creation, or password change request.
- **Main Flow:**
  1. User or administrator submits a password creation or update request.
  2. Backend validates password policy and hashes the password with `DelegatingPasswordEncoder` before persisting.
- **Exception Behavior:**
  - *E1 (Weak or missing password):* Backend rejects the request with `400 Bad Request` Problem Details and field validation errors.
- **Acceptance Criteria:**
  - **AC1:** Given a newly created or updated user account, when its database row is inspected, then the stored password column contains a `{bcrypt}` (or stronger) encoded hash and never plaintext.
  - **AC2:** Given any authentication or password change request, when application logs and API responses are inspected, then no password, secret, or token value is present.
- **Dependencies:** None
- **Related Business Rules / Use Cases:** `BRULE-029`, `UC-HO-03`

---

## 2. User, Employee, Role, and Permission Management (`FR-004` to `FR-006`)

### FR-004: Staff Account Lifecycle Management
- **Classification:** CONFIRMED (branch assignment & audit `CLAUDE.md` §8; roles `B5` PROPOSED)
- **Priority:** Must Have (MVP)
- **Source:** `REQ-011`, `CLAUDE.md` §8, `BRULE-011`, `BRULE-030`, `B5`
- **Description & Rationale:** System Owners and Head Office Administrators need to create, update, activate, and deactivate employee accounts so staff access can be controlled centrally as personnel change across the 5–30 branches.
- **Preconditions:** Actor is authenticated as `SYSTEM_OWNER` or `HEAD_OFFICE_ADMIN`.
- **Main Flow:**
  1. Administrator submits a request to create or update a staff account (name, email, phone, active status, initial password).
  2. Backend validates uniqueness of email/username, persists the staff record, and writes a security audit record.
- **Alternative Flows:**
  - *A1 (Deactivate employee):* Administrator deactivates an employee account; active sessions/tokens for that employee are rejected on subsequent requests.
- **Exception Behavior:**
  - *E1 (Unauthorized role):* A branch-level user attempts to create or modify a staff account (unless delegated by `B5`); backend returns `403 Forbidden`.
  - *E2 (Duplicate email):* Backend returns `400`/`409` Problem Details.
- **Acceptance Criteria:**
  - **AC1:** Given a `SYSTEM_OWNER` or `HEAD_OFFICE_ADMIN`, when they create a staff account with valid details, then the account is persisted and an audit log entry is recorded.
  - **AC2:** Given an active staff member whose account is switched to `DISABLED`, when that staff member makes an API call, then the backend denies access with `401`/`403`.
- **Dependencies:** `FR-001`, `FR-003`
- **Related Business Rules / Use Cases:** `BRULE-011`, `BRULE-030`, `UC-HO-03`

### FR-005: Role and Branch Scope Assignment for Staff
- **Classification:** CONFIRMED (`REQ-011`, `REQ-012`); Role set PROPOSED (`B5`)
- **Priority:** Must Have (MVP)
- **Source:** `REQ-011`, `REQ-012`, `CLAUDE.md` §8, `BRULE-011`, `BRULE-012`, `BRULE-030`, `B5`
- **Description & Rationale:** Every staff account must be assigned one or more roles (`SYSTEM_OWNER`, `HEAD_OFFICE_ADMIN`, `BRANCH_MANAGER`, `SALES_STAFF`, `INVENTORY_STAFF`, `DELIVERY_STAFF`) and either global chain scope (Head Office roles) or an explicit set of authorized `branchId`s (branch roles).
- **Preconditions:** Target staff account and target branch(es) exist. Actor is `SYSTEM_OWNER` or `HEAD_OFFICE_ADMIN`.
- **Main Flow:**
  1. Administrator assigns or updates the role(s) and authorized `branchId`s for a staff user.
  2. Backend validates that every branch-scoped role (`BRANCH_MANAGER`, `SALES_STAFF`, `INVENTORY_STAFF`, `DELIVERY_STAFF`) is linked to at least one valid `branchId`.
  3. Backend persists the assignment and writes a role/branch-assignment change audit entry (`BRULE-030`: actor, target user, previous roles/branches, new roles/branches, UTC timestamp).
- **Exception Behavior:**
  - *E1 (Branch role without a branchId):* Backend rejects the request with `400 Bad Request`.
- **Acceptance Criteria:**
  - **AC1:** Given staff user U is assigned role `BRANCH_MANAGER` for Branch A, when U's role or branch assignment is saved, then an audit record is written capturing who made the change, when, and the old/new assignments (`BRULE-030`).
  - **AC2:** Given a request to assign a branch-scoped role with zero branches, when submitted, then the backend returns `400 Bad Request`.
- **Dependencies:** `FR-004`, `FR-007`
- **Related Business Rules / Use Cases:** `BRULE-011`, `BRULE-012`, `BRULE-030`, `UC-HO-03`

### FR-006: Server-Side Branch-Level Authorization Enforcement
- **Classification:** CONFIRMED (Non-negotiable baseline rule)
- **Priority:** Must Have (MVP)
- **Source:** `REQ-011`, `REQ-012`, `CLAUDE.md` §8, `BRULE-011`, `BRULE-012`
- **Description & Rationale:** Every backend service method and query operating on branch-owned data (inventory, branch prices, branch orders, stock transfers, goods receipts, spoilage adjustments) must resolve authorized `branchId`s from the authenticated principal on the server and never trust a client-supplied `branchId` without verifying authorization.
- **Preconditions:** Authenticated request reaches a branch-scoped endpoint or service.
- **Main Flow:**
  1. Backend extracts the authenticated user's authorized `branchId`s from the server security context.
  2. If the request includes a `branchId` (in path, query, or JSON body), the backend verifies that `branchId` is in the user's authorized set (or that the user has a global Head Office role permitted for that action).
  3. If the request lists branch-owned resources without a `branchId`, the repository query automatically filters by `WHERE branch_id IN (:authorizedBranchIds)`.
- **Exception Behavior:**
  - *E1 (Cross-branch access or forged `branchId`):* User authorized only for Branch A sends `branchId = Branch B` or targets a record owned by Branch B; backend rejects the request with `403 Forbidden` (or `404 Not Found` where existence should not be leaked) and performs no mutation.
- **Acceptance Criteria:**
  - **AC1:** Given a staff user authorized only for Branch A, when they request or mutate inventory, prices, orders, or transfers for Branch B (via path, query, or body `branchId`), then the backend denies the request with `403` or `404` and Branch B's data remains untouched.
  - **AC2:** Given a staff user authorized only for Branch A, when they call a list endpoint for orders or inventory, then the response contains only records with `branchId = A`.
- **Dependencies:** `FR-001`, `FR-005`
- **Related Business Rules / Use Cases:** `BRULE-011`, `BRULE-012`, `UC-BR-01`..`10`

---

## 3. Organization and Branch Management (`FR-007` to `FR-009`)

### FR-007: Branch Creation and Lifecycle Management
- **Classification:** CONFIRMED (`REQ-001`)
- **Priority:** Must Have (MVP)
- **Source:** `REQ-001`, `BRULE-001`, `UC-HO-01`
- **Description & Rationale:** The single business owner operates 5–30 branches (`REQ-001`). Head Office administrators must be able to create, update, activate, and deactivate branches.
- **Preconditions:** Actor is authenticated as `SYSTEM_OWNER` or `HEAD_OFFICE_ADMIN`.
- **Main Flow:**
  1. Administrator creates or updates a branch with branch code, name, street address, district/city, phone, operating hours, and status (`ACTIVE` / `INACTIVE`).
  2. Backend validates uniqueness of branch code and persists the branch.
- **Alternative Flows:**
  - *A1 (Deactivate branch):* An `INACTIVE` branch is excluded from new online delivery allocations (`REQ-014`) and new pickup selections (`REQ-013`), while preserving historical orders (`REQ-005`) and audit trails (`REQ-008`).
- **Exception Behavior:**
  - *E1 (Duplicate branch code):* Backend returns `400`/`409` Problem Details.
- **Acceptance Criteria:**
  - **AC1:** Given a `SYSTEM_OWNER` or `HEAD_OFFICE_ADMIN`, when they create a branch with unique code and valid address details, then the branch is persisted and retrievable.
  - **AC2:** Given a branch whose status is set to `INACTIVE`, when a customer places a new online order, then that branch is not selected for delivery allocation or offered for pickup.
- **Dependencies:** `FR-001`
- **Related Business Rules / Use Cases:** `BRULE-001`, `BRULE-018`, `UC-HO-01`

### FR-008: Branch Geolocation, Delivery Zone, and Operating Hours Configuration
- **Classification:** INFERRED (`REQ-013`, `REQ-014`); distance formula NEEDS CONFIRMATION (`B3e`)
- **Priority:** Must Have (MVP)
- **Source:** `REQ-013`, `REQ-014`, `BRULE-014`, `BRULE-018`, `B3e`, `B15`
- **Description & Rationale:** To automatically pick the nearest branch to a customer's delivery address (`REQ-014`) and validate pickup/delivery windows (`B15`), each branch must store geographic coordinates (`latitude`, `longitude`) and/or supported delivery zones/radius and operating hours.
- **Preconditions:** Branch exists; actor is `SYSTEM_OWNER` or `HEAD_OFFICE_ADMIN`.
- **Main Flow:**
  1. Administrator configures the branch's coordinates (`latitude`, `longitude`), maximum delivery radius / covered districts (`B3e`), and daily operating hours.
  2. Backend validates coordinate ranges (`lat` in `[-90, 90]`, `lng` in `[-180, 180]`, radius `> 0`) and saves the configuration.
- **Exception Behavior:**
  - *E1 (Invalid coordinates or negative radius):* Backend returns `400 Bad Request` Problem Details.
- **Acceptance Criteria:**
  - **AC1:** Given valid latitude, longitude, delivery radius/zones, and operating hours, when saved for a branch, then the branch allocation service can read those values to compute proximity for delivery orders (`FR-021`).
  - **AC2:** Given out-of-range latitude/longitude or negative radius, when submitted, then the backend rejects the request with `400 Bad Request`.
- **Dependencies:** `FR-007`
- **Related Business Rules / Use Cases:** `BRULE-014`, `BRULE-018`, `UC-HO-01`

### FR-009: Public Storefront Branch Directory
- **Classification:** INFERRED (`REQ-013`, `REQ-017`)
- **Priority:** Must Have (MVP)
- **Source:** `REQ-013`, `REQ-017`, `B3a`, `B3c`, `B11`
- **Description & Rationale:** Customers need to view active branches (name, address, operating hours) on the storefront to choose a browsing branch (`B11`) or an in-store pickup location (`REQ-013`, `B3c`).
- **Preconditions:** At least one `ACTIVE` branch exists.
- **Main Flow:**
  1. Storefront requests `/api/v1/storefront/branches`.
  2. Backend returns public details of all `ACTIVE` branches (excluding internal staff or operational metrics).
- **Acceptance Criteria:**
  - **AC1:** Given 5 active branches and 1 inactive branch, when `/api/v1/storefront/branches` is called, then only the 5 active branches and their public location/hours data are returned.
- **Dependencies:** `FR-007`, `FR-008`
- **Related Business Rules / Use Cases:** `BRULE-001`, `BRULE-013`, `BRULE-017`, `UC-CUST-01`, `UC-CUST-04`

---

## 4. Product Catalog and Variants (`FR-010` to `FR-012`)

### FR-010: Central Product Catalog and Category Management
- **Classification:** CONFIRMED (`REQ-003`)
- **Priority:** Must Have (MVP)
- **Source:** `REQ-003`, `BRULE-003`, `UC-HO-02`
- **Description & Rationale:** The product catalog (categories, products, SKUs, descriptions, images, active status) is managed centrally by Head Office (`REQ-003`).
- **Preconditions:** Actor is authenticated as `SYSTEM_OWNER` or `HEAD_OFFICE_ADMIN`.
- **Main Flow:**
  1. Head Office administrator creates or updates product categories and products (unique `sku`, name, description, category, image URLs, unit, `ACTIVE`/`ARCHIVED` status).
  2. Backend validates uniqueness of `sku` and required fields, then persists the catalog item.
- **Alternative Flows:**
  - *A1 (Archive product):* Archiving a product hides it from new storefront orders while keeping historical order snapshots intact (`REQ-006`).
- **Exception Behavior:**
  - *E1 (Branch staff attempts to create/edit central catalog item):* Backend rejects with `403 Forbidden` (`REQ-003`).
  - *E2 (Duplicate SKU):* Backend rejects with `400`/`409` Problem Details.
- **Acceptance Criteria:**
  - **AC1:** Given a `HEAD_OFFICE_ADMIN`, when they create a product with a unique SKU, name, and category, then the product is available in the central catalog across all branches.
  - **AC2:** Given a branch-scoped user (`BRANCH_MANAGER`, `SALES_STAFF`, `INVENTORY_STAFF`), when they attempt to create or modify a central product or SKU, then the backend denies the request with `403 Forbidden`.
  - **AC3:** Given a product that was ordered in the past, when a `HEAD_OFFICE_ADMIN` updates the product's name or archives it, then existing orders still display the original snapshotted product name and SKU (`REQ-006`).
- **Dependencies:** `FR-001`, `FR-006`
- **Related Business Rules / Use Cases:** `BRULE-003`, `BRULE-006`, `UC-HO-02`

### FR-011: Product Variants and Seasonal Availability Windows
- **Classification:** PROPOSED / NEEDS CONFIRMATION (`B18`)
- **Priority:** Could Have (Conditional on `B18`)
- **Source:** P03 Business Analysis §2.4, `B18`, `UC-HO-02`
- **Description & Rationale:** Floral shops often sell bouquet size variants (e.g., Standard, Deluxe, Premium — each represented as a distinct orderable SKU) or seasonal items (e.g., Tet, Valentine's Day, Women's Day) with start/end availability dates.
- **Preconditions:** Central catalog exists (`FR-010`).
- **Main Flow:**
  1. Head Office configures optional variant attributes (represented as distinct SKUs so inventory and branch prices remain 1:1 per SKU) and optional seasonal `availableFrom` / `availableUntil` UTC timestamps.
  2. Storefront catalog queries automatically filter out products outside their active seasonal window.
- **Acceptance Criteria:**
  - **AC1 (Pending `B18`):** Given a seasonal product with `availableFrom` in the future or `availableUntil` in the past, when a customer browses the storefront catalog, then the product is not orderable.
- **Dependencies:** `FR-010`
- **Related Business Rules / Use Cases:** `BRULE-003`, `UC-HO-02`

### FR-012: Branch-Specific Product Visibility Override
- **Classification:** NEEDS CONFIRMATION (`B10`)
- **Priority:** Could Have (Conditional on `B10`)
- **Source:** `REQ-003`, `B10`, `BRULE-003`, `BRULE-020`
- **Description & Rationale:** Determines whether a branch may locally hide/disable a central catalog product that it does not carry (`B10`). By default (`REQ-003`), the catalog is strictly central unless `B10` approves local visibility toggles.
- **Preconditions:** Product exists in central catalog (`FR-010`); branch exists (`FR-007`).
- **Main Flow:**
  1. If approved under `B10`, authorized user toggles `isSellableAtBranch` for a `(branchId, productId)` pair.
- **Acceptance Criteria:**
  - **AC1 (Pending `B10`):** Given a product that is either not priced or marked disabled at Branch A, when Branch A is evaluated for order fulfilment or browsing, then that product cannot be ordered from Branch A (`BRULE-020`).
- **Dependencies:** `FR-007`, `FR-010`, `FR-013`
- **Related Business Rules / Use Cases:** `BRULE-003`, `BRULE-020`, `UC-HO-02`, `UC-BR-09`

---

## 5. Branch Pricing (`FR-013` to `FR-014`)

### FR-013: Per-Branch Product Price Configuration and Price-Change Audit Trail
- **Classification:** CONFIRMED (`REQ-004`, `B9`, `CLAUDE.md` §8); Who sets prices NEEDS CONFIRMATION (`B9b`)
- **Priority:** Must Have (MVP)
- **Source:** `REQ-004`, `B9`, `B9b`, `CLAUDE.md` §5, §6, §8, `BRULE-004`, `BRULE-019`
- **Description & Rationale:** Each branch has its own product prices (`REQ-004`, `B9`). Authorized users must configure prices per `(branchId, productId)` using exact decimal arithmetic (`BigDecimal` / PostgreSQL `numeric`), and every price change must be audited (`BRULE-019`).
- **Preconditions:** Branch (`FR-007`) and product (`FR-010`) exist. Actor is authorized to set prices for `branchId` (`B9b`: `HEAD_OFFICE_ADMIN` and/or `BRANCH_MANAGER` for their own branch).
- **Main Flow:**
  1. Authorized actor sets or updates the unit price (`>= 0`, `BigDecimal`) for a product at a specific branch.
  2. Backend verifies branch-level authorization (`REQ-011`, `REQ-012`).
  3. Backend updates the branch price and writes an immutable price-change audit entry (`BRULE-019`: actor ID, UTC timestamp, `branchId`, `productId`, `oldPrice`, `newPrice`).
- **Exception Behavior:**
  - *E1 (Negative price):* Backend rejects with `400 Bad Request`.
  - *E2 (Cross-branch price update):* Branch Manager of Branch A attempts to change Branch B's price; backend denies with `403 Forbidden` (`REQ-011`, `REQ-012`).
- **Acceptance Criteria:**
  - **AC1:** Given Branch A's price for Product P is set to `500000` and Branch B's price for Product P is set to `550000`, when prices for P are queried per branch, then Branch A returns `500000` and Branch B returns `550000` (`REQ-004`).
  - **AC2:** Given Branch A's price for Product P is changed from `500000` to `520000`, when the change commits, then Branch B's price remains `550000` and a price-change audit record is persisted with actor, UTC timestamp, `branchId = A`, `productId = P`, `oldPrice = 500000`, and `newPrice = 520000` (`BRULE-019`).
  - **AC3:** Given a user authorized only for Branch A, when they attempt to update a price for Branch B, then the backend returns `403 Forbidden` (`REQ-011`, `REQ-012`).
- **Dependencies:** `FR-006`, `FR-007`, `FR-010`
- **Related Business Rules / Use Cases:** `BRULE-004`, `BRULE-011`, `BRULE-012`, `BRULE-019`, `UC-BR-09`

### FR-014: Storefront Branch Price Display and Checkout Price Reconciliation
- **Classification:** NEEDS CONFIRMATION (`REQ-017`, `B11`)
- **Priority:** Must Have (MVP)
- **Source:** `REQ-004`, `REQ-006`, `REQ-014`, `REQ-017`, `B9`, `B11`, `BRULE-017`
- **Description & Rationale:** Resolves the interaction between per-branch storefront pricing (`REQ-017`, `B9`) and automatic nearest-branch selection for delivery orders (`REQ-014`, `B3b`).
- **Preconditions:** Branch prices are configured (`FR-013`).
- **Main Flow (PROPOSED pending `B11` confirmation):**
  1. While browsing the storefront, the customer selects a browsing branch/area (or sees a default active branch) and views that branch's prices (`REQ-017`).
  2. For an **in-store pickup** order, the customer pays the price of the selected pickup branch.
  3. For a **home delivery** order, once the system auto-selects the nearest branch with sufficient stock (`REQ-014`), if that processing branch's price differs from the browsing branch's price, the checkout summary displays the processing branch's exact price and requires explicit customer confirmation before order placement and payment snapshot creation (`REQ-006`).
- **Acceptance Criteria:**
  - **AC1:** Given Branch A and Branch B have different prices for Product P, when a customer selects Branch A on the storefront, then the storefront displays Branch A's price for P (`REQ-017`).
  - **AC2 (Pending `B11`):** Given a customer browsed under Branch A's price (`500000`), and at delivery checkout the system auto-selects Branch B (`550000`) as the processing branch, when the order quote is calculated, then the quote clearly flags the price change to `550000` and the order is only placed and snapshotted (`REQ-006`) after the customer confirms that price.
- **Dependencies:** `FR-009`, `FR-013`, `FR-021`
- **Related Business Rules / Use Cases:** `BRULE-004`, `BRULE-006`, `BRULE-014`, `BRULE-017`, `UC-CUST-01`, `UC-CUST-03`

---

## 6. Inventory, Stock Reservation, Transfers, and Adjustments (`FR-015` to `FR-019`)

### FR-015: Branch-Isolated Inventory Tracking and Non-Negative Guarantee
- **Classification:** CONFIRMED (Non-negotiable baseline rules `REQ-002`, `REQ-007`, `REQ-016`)
- **Priority:** Must Have (MVP)
- **Source:** `REQ-002`, `REQ-007`, `REQ-016`, `B2`, `CLAUDE.md` §6, §9, `BRULE-002`, `BRULE-007`, `BRULE-016`
- **Description & Rationale:** All stock belongs to a specific branch (`branch_id NOT NULL` foreign key, `REQ-002`); there is no central warehouse (`REQ-016`). Available stock at any branch must never become negative (`available_stock >= 0`), enforced both by row-level/optimistic locking in the application service and by a PostgreSQL `CHECK` constraint (`REQ-007`).
- **Preconditions:** Branch (`FR-007`) and Product (`FR-010`) exist.
- **Main Flow:**
  1. System tracks inventory per `(branch_id, product_id)` with `available_stock >= 0` (and `reserved_stock >= 0` if two-step reservation is used).
  2. Every operation that decrements `available_stock` acquires a lock on the `(branch_id, product_id)` row, verifies `available_stock >= requested_deduction`, and commits within a transaction.
- **Exception Behavior:**
  - *E1 (Insufficient stock / concurrent race):* If `available_stock < requested_deduction`, the transaction rejects the deduction (`409 Conflict` / domain error) and `available_stock` never drops below `0`.
- **Acceptance Criteria:**
  - **AC1:** Given any inventory record in the database, when inspected, then it references a non-null `branch_id` foreign key and no central-warehouse inventory record exists (`REQ-002`, `REQ-016`).
  - **AC2:** Given Branch A has `available_stock = 1` for Product P, when 10 concurrent requests attempt to deduct 1 unit of P from Branch A simultaneously, then exactly 1 request succeeds, 9 fail cleanly, and Branch A's `available_stock` ends at `0` (never `< 0`) (`REQ-007`).
  - **AC3:** Given a direct SQL attempt to update `available_stock` to `-1`, when executed against PostgreSQL, then the database `CHECK` constraint rejects the statement (`CLAUDE.md` §6).
- **Dependencies:** `FR-007`, `FR-010`
- **Related Business Rules / Use Cases:** `BRULE-002`, `BRULE-007`, `BRULE-016`, `UC-BR-05`

### FR-016: Mandatory Inventory Audit Ledger
- **Classification:** CONFIRMED (Non-negotiable baseline rule `REQ-008`)
- **Priority:** Must Have (MVP)
- **Source:** `REQ-008`, `CLAUDE.md` §6, `BRULE-008`
- **Description & Rationale:** Every change to a branch's inventory must write an immutable audit record in the same database transaction capturing who made the change, when (UTC), why (reason/movement type), which branch, which product, the quantity delta (`+N` or `-N`), resulting balance, and the source document ID (`REQ-008`).
- **Preconditions:** Any inventory mutation occurs (`FR-017`, `FR-018`, `FR-019`, `FR-020`).
- **Main Flow:**
  1. Within the same database transaction that modifies branch stock, the inventory service inserts an immutable row into the inventory audit table.
  2. Authorized staff (`BRANCH_MANAGER` for their branch, `HEAD_OFFICE_ADMIN`/`SYSTEM_OWNER` for all branches) can query the paginated audit trail.
- **Exception Behavior:**
  - *E1 (Audit write failure):* Transaction rolls back completely so stock cannot change without an audit entry.
- **Acceptance Criteria:**
  - **AC1:** Given any inventory change (order reservation/deduction, cancellation restock, goods receipt, spoilage/adjustment, or inter-branch transfer), when the transaction commits, then an audit row exists containing `actor_id`, `created_at` (UTC), `reason_code`, `branch_id`, `product_id`, `quantity_delta`, and `source_document_id` (`REQ-008`).
  - **AC2:** Given the inventory audit table, when application APIs are inspected, then no endpoint exists to update or delete historical audit rows.
- **Dependencies:** `FR-006`, `FR-015`
- **Related Business Rules / Use Cases:** `BRULE-008`, `BRULE-011`, `UC-BR-05`, `UC-HO-07`

### FR-017: Order Stock Reservation, Deduction, and Expiration/Cancellation Release
- **Classification:** CONFIRMED (`REQ-007`, `REQ-008`); Reservation timing PROPOSED / NEEDS CONFIRMATION (`B13`)
- **Priority:** Must Have (MVP)
- **Source:** `REQ-007`, `REQ-008`, `REQ-014`, `B3b`, `B13`, `BRULE-007`, `BRULE-008`
- **Description & Rationale:** When an online order is allocated to a processing branch (`REQ-014`), stock for the ordered items must be reserved/deducted so concurrent shoppers cannot oversell the last units (`REQ-007`), and released back to available stock if payment fails/times out or the order is cancelled before preparation (`B13`).
- **Preconditions:** Processing branch has `available_stock >= ordered_quantity` for every order line (`FR-015`).
- **Main Flow (PROPOSED pending `B13`):**
  1. At order placement, the backend locks the processing branch's inventory rows, decrements `available_stock` by the ordered quantity (moving it to `reserved_stock` for pending online payment, or deducting directly), and writes an `ORDER_RESERVED` / `ORDER_DEDUCTED` inventory audit entry (`REQ-008`).
  2. When payment is verified (`FR-025`), reservation is finalized.
  3. If payment fails/expires or the order is cancelled prior to preparation (`FR-024`), the backend restores `+ordered_quantity` to `available_stock` at that branch and writes an `ORDER_RELEASED` inventory audit entry (`REQ-008`).
- **Acceptance Criteria:**
  - **AC1:** Given Branch A has 3 units of Product P available, when an order for 2 units of P is placed at Branch A, then Branch A's `available_stock` becomes 1 and an inventory audit record of `-2` linked to the `orderId` is created (`REQ-007`, `REQ-008`).
  - **AC2 (Pending `B13`):** Given the order in AC1 is cancelled before preparation (or its payment expires), when the cancellation commits, then Branch A's `available_stock` returns to 3 and a `+2` inventory audit record linked to the `orderId` is created (`REQ-008`).
- **Dependencies:** `FR-015`, `FR-016`
- **Related Business Rules / Use Cases:** `BRULE-007`, `BRULE-008`, `BRULE-014`, `UC-CUST-03`, `UC-CUST-04`

### FR-018: Inter-Branch Stock Transfers
- **Classification:** CONFIRMED (`REQ-015`, `B2`); Workflow states & discrepancies PROPOSED / NEEDS CONFIRMATION (`B12`, `B16`)
- **Priority:** Must Have (MVP)
- **Source:** `REQ-007`, `REQ-008`, `REQ-011`, `REQ-015`, `B2`, `B12`, `B16`, `BRULE-007`, `BRULE-008`, `BRULE-015`, `WF-11`
- **Description & Rationale:** Branches can transfer stock to each other, and every transfer is recorded (`REQ-015`, `B2`). Must respect branch-level authorization (`REQ-011`, `REQ-012`), non-negative stock (`REQ-007`), and paired audit entries (`REQ-008`).
- **Preconditions:** Sending branch (`sourceBranchId`) and receiving branch (`targetBranchId`) are distinct active branches.
- **Main Flow (PROPOSED multi-step lifecycle pending `B12`, `B16`):**
  1. **Request / Approve:** Authorized user creates a transfer request from Branch A to Branch B with items and quantities (`> 0`). Authorized approver (`B12`) approves the transfer.
  2. **Dispatch from Sending Branch:** When Branch A dispatches the transfer, the backend locks Branch A's inventory, verifies `available_stock >= transfer_qty` (`REQ-007`), deducts `-transfer_qty` from Branch A (`available_stock`), marks the transfer `IN_TRANSIT` (or completes in one step if `B12` chooses single-step), and writes a `-transfer_qty` audit entry at Branch A linked to `transferId` (`REQ-008`).
  3. **Receive at Receiving Branch:** When Branch B confirms receipt, the backend increments Branch B's `available_stock` by `+received_qty`, marks the transfer `COMPLETED`, and writes a `+received_qty` audit entry at Branch B linked to `transferId` (`REQ-008`).
- **Alternative / Exception Behavior:**
  - *E1 (Insufficient stock at sending branch):* If Branch A holds 3 units and a transfer of 5 units is attempted/dispatched, the backend rejects the dispatch (`409 Conflict`) and Branch A's available stock never goes below zero (`REQ-007`).
  - *E2 (Transit damage / shortfall — pending `B16`):* If Branch B receives fewer undamaged units than dispatched, the received undamaged quantity (`+received_qty`) is added to Branch B's available stock and the shortfall is recorded with a discrepancy reason and audit entry (`B16`).
  - *E3 (Unauthorized branch user):* A user authorized for Branch C attempts to dispatch or receive a transfer between Branch A and Branch B; backend denies with `403 Forbidden` (`REQ-011`, `REQ-012`).
- **Acceptance Criteria:**
  - **AC1:** Given Branch A holds 10 units of Product P and Branch B holds 2 units, when a transfer of 4 units of P from A to B is completed, then Branch A holds 6 units and Branch B holds 6 units (`REQ-015`).
  - **AC2:** Given the completed transfer in AC1, when the inventory audit trail is queried, then it contains a `-4` entry for Branch A and a `+4` entry for Branch B, both referencing the transfer ID, actor, and UTC timestamp (`REQ-008`, `REQ-015`).
  - **AC3:** Given Branch A holds 3 units of Product P, when a transfer deduction of 5 units out of Branch A is attempted, then the operation is rejected and Branch A's available stock remains `3` (`>= 0`, `REQ-007`).
- **Dependencies:** `FR-006`, `FR-015`, `FR-016`
- **Related Business Rules / Use Cases:** `BRULE-007`, `BRULE-008`, `BRULE-011`, `BRULE-012`, `BRULE-015`, `UC-BR-08`, `UC-HO-04`

### FR-019: Spoilage, Damaged Goods Write-Off, and Stocktake Adjustments
- **Classification:** INFERRED (`REQ-002`, `REQ-007`, `REQ-008`); Approval threshold NEEDS CONFIRMATION (`B19`)
- **Priority:** Must Have (MVP)
- **Source:** `REQ-002`, `REQ-007`, `REQ-008`, `REQ-011`, `B19`, `BRULE-007`, `BRULE-008`, `BRULE-021`, `WF-10`
- **Description & Rationale:** Fresh flowers are perishable. Authorized branch staff must be able to deduct spoiled/wilted or damaged stock and record physical stocktake corrections for their branch while enforcing non-negative stock (`REQ-007`) and full auditability (`REQ-008`).
- **Preconditions:** Actor is authenticated and authorized for `branchId` (`REQ-011`, `REQ-012`).
- **Main Flow:**
  1. Staff user submits an inventory adjustment for their authorized `branchId` specifying `productId`, `quantityDelta` (negative for `SPOILAGE` or `DAMAGED`; positive or negative for `STOCKTAKE_ADJUSTMENT`), and a mandatory reason note.
  2. Backend verifies branch authorization (`REQ-011`, `REQ-012`), locks the branch inventory row, and verifies `available_stock + quantityDelta >= 0` (`REQ-007`).
  3. Backend updates `available_stock` and inserts an inventory audit entry (`REQ-008`) referencing the adjustment document.
- **Exception Behavior:**
  - *E1 (Deduction exceeds available stock):* Backend rejects with `400`/`409` Problem Details; stock stays `>= 0` (`REQ-007`).
  - *E2 (Cross-branch adjustment):* Backend rejects with `403 Forbidden` (`REQ-011`, `REQ-012`).
- **Acceptance Criteria:**
  - **AC1:** Given Branch A has 8 units of Product P, when an authorized Branch A user records a `SPOILAGE` write-off of `-3` units with a note, then Branch A's `available_stock` becomes 5 and an audit record with `reason = SPOILAGE` and `delta = -3` is written (`REQ-008`, `BRULE-021`).
  - **AC2:** Given Branch A has 2 units of Product P, when a `SPOILAGE` write-off of `-5` units is submitted, then the backend rejects the request and Branch A's `available_stock` remains 2 (`REQ-007`).
- **Dependencies:** `FR-006`, `FR-015`, `FR-016`
- **Related Business Rules / Use Cases:** `BRULE-007`, `BRULE-008`, `BRULE-011`, `BRULE-012`, `BRULE-021`, `UC-BR-07`

---

## 7. Suppliers and Purchasing / Goods Receiving (`FR-020`)

### FR-020: Branch Goods Receiving (Stock-In)
- **Classification:** INFERRED (`REQ-002`, `REQ-008`, `REQ-016`); Supplier PO scope NEEDS CONFIRMATION (`B19`)
- **Priority:** Must Have (MVP)
- **Source:** `REQ-002`, `REQ-008`, `REQ-011`, `REQ-016`, `B2`, `B19`, `WF-09`, `UC-BR-06`
- **Description & Rationale:** Because there is no central warehouse (`REQ-016`, `B2`), stock enters the business directly at branches. Authorized branch staff must be able to record Goods Receipts (stock-in) at their branch to increase `available_stock` and create an audit trail (`REQ-008`). (Full supplier Purchase Order management is out of MVP scope unless requested under `B19`.)
- **Preconditions:** Actor is authorized for `branchId` (`REQ-011`, `REQ-012`); product exists in the central catalog (`FR-010`).
- **Main Flow:**
  1. Authorized staff member submits a Goods Receipt for their branch with supplier name/reference note and one or more lines (`productId`, `receivedQuantity > 0`).
  2. Backend verifies branch authorization (`REQ-011`, `REQ-012`), increments `available_stock` for each product at `branchId`, and writes a `GOODS_RECEIPT` (`+receivedQuantity`) inventory audit record for each line (`REQ-008`).
- **Exception Behavior:**
  - *E1 (Zero or negative received quantity):* Backend rejects with `400 Bad Request`.
  - *E2 (Unauthorized branchId):* Backend rejects with `403 Forbidden` (`REQ-011`, `REQ-012`).
- **Acceptance Criteria:**
  - **AC1:** Given Branch A has 0 units of Product P, when an authorized Branch A staff member records a Goods Receipt of 25 units of P, then Branch A's `available_stock` becomes 25 and an audit record with `reason = GOODS_RECEIPT`, `delta = +25`, actor ID, and receipt ID is created (`REQ-002`, `REQ-008`).
- **Dependencies:** `FR-006`, `FR-010`, `FR-015`, `FR-016`
- **Related Business Rules / Use Cases:** `BRULE-002`, `BRULE-008`, `BRULE-011`, `BRULE-012`, `BRULE-016`, `UC-BR-06`

---

## 8. Orders, Allocation, and Fulfillment (`FR-021` to `FR-024`)

### FR-021: Online Order Placement, Automatic Branch Allocation, and Snapshot Persistence
- **Classification:** CONFIRMED (`REQ-005`, `REQ-006`, `REQ-013`, `REQ-014`); Pickup store (`B3c`), shortfall (`B3d`), distance metric (`B3e`) NEEDS CONFIRMATION
- **Priority:** Must Have (MVP)
- **Source:** `REQ-005`, `REQ-006`, `REQ-007`, `REQ-008`, `REQ-012`, `REQ-013`, `REQ-014`, `B3a`, `B3b`, `CLAUDE.md` §6, §7
- **Description & Rationale:** Customers place online orders choosing **home delivery** or **in-store pickup** (`REQ-013`). Every order must record a traceable `processingBranchId` (`REQ-005`), copy immutable product/price snapshots onto order lines (`REQ-006`), reserve/deduct branch stock atomically (`REQ-007`, `REQ-008`), and support `Idempotency-Key` retries (`CLAUDE.md` §7).
- **Preconditions:** Cart contains active catalog products with quantities `> 0`.
- **Main Flow:**
  1. Customer submits an order request with `Idempotency-Key` header, fulfilment method (`DELIVERY` or `PICKUP`), line items (`productId`, `quantity`), and recipient details.
  2. **Branch Allocation (`REQ-005`, `REQ-014`):**
     - If `DELIVERY`: backend ignores any client-sent `branchId` (`REQ-012`, `REQ-014`), finds all active branches within delivery range (`B3e`) that hold `available_stock >= quantity` for every item (`B3d`), and selects the nearest branch as `processingBranchId`.
     - If `PICKUP` (PROPOSED pending `B3c`): backend validates the customer-selected pickup branch is active and holds `available_stock >= quantity` for every item, setting it as `processingBranchId`.
  3. **Order Snapshots (`REQ-006`):** For each line item, the backend copies `product_name`, `sku`, and the branch's `unit_price` (`BigDecimal`) at order time onto the `order_lines` record.
  4. **Inventory Reservation/Deduction (`REQ-007`, `REQ-008`):** Backend locks and decrements `available_stock` at `processingBranchId` (`FR-017`) and writes inventory audit records.
  5. Backend persists the order with `processingBranchId` (`branch_id NOT NULL`) and returns the created order summary.
- **Alternative Flows:**
  - *A1 (Idempotent retry):* Client retries the same order request with the same `Idempotency-Key`; backend returns the original created order without creating a duplicate order or deducting stock twice.
- **Exception Behavior:**
  - *E1 (No single branch has sufficient stock — pending `B3d`):* Backend rejects the order with `409 Conflict` Problem Details specifying unavailable items, and no stock is deducted.
  - *E2 (Address outside delivery area — pending `B3e`):* Backend rejects with `422`/`400` Problem Details.
- **Acceptance Criteria:**
  - **AC1:** Given a delivery order where active branches A and B both have enough stock for all items and Branch A is nearer the delivery address than Branch B, when the order is placed, then `processingBranchId` is set to Branch A (`REQ-005`, `REQ-014`).
  - **AC2:** Given a delivery order where nearest Branch A lacks stock for at least one item and next-nearest Branch B has enough stock for all items, when the order is placed, then `processingBranchId` is set to Branch B (`REQ-014`).
  - **AC3:** Given a delivery order payload that includes `"branchId": "<BranchC>"`, when placed, then the backend ignores `<BranchC>` and assigns `processingBranchId` strictly by the nearest-with-stock rule (`REQ-012`, `REQ-014`).
  - **AC4:** Given an order placed when Product P's name is `"Red Rose Bouquet"`, SKU is `"RRB-01"`, and unit price at the processing branch is `450000`, when Product P's catalog name or branch price is later changed, then querying the historical order still returns `"Red Rose Bouquet"`, `"RRB-01"`, and `450000` (`REQ-006`).
- **Dependencies:** `FR-008`, `FR-010`, `FR-013`, `FR-014`, `FR-015`, `FR-016`, `FR-017`
- **Related Business Rules / Use Cases:** `BRULE-005`, `BRULE-006`, `BRULE-007`, `BRULE-008`, `BRULE-012`, `BRULE-013`, `BRULE-014`, `BRULE-022`, `BRULE-026`, `UC-CUST-03`, `UC-CUST-04`

### FR-022: Branch Order Queue and Fulfilment Status Lifecycle
- **Classification:** CONFIRMED (branch-scoped order handling `REQ-005`, `REQ-011`, status audit `CLAUDE.md` §8); Exact status machine PROPOSED / NEEDS CONFIRMATION (`B17`)
- **Priority:** Must Have (MVP)
- **Source:** `REQ-005`, `REQ-011`, `REQ-012`, `REQ-013`, `CLAUDE.md` §8, `B17`, `BRULE-005`, `BRULE-011`, `BRULE-023`, `WF-06`
- **Description & Rationale:** Staff at the processing branch prepare the order and move it through its fulfilment lifecycle until hand-over (`WF-06`, `WF-07`), with every status change audited (`BRULE-023`).
- **Preconditions:** Staff user is authenticated and authorized for the order's `processingBranchId` (`REQ-011`, `REQ-012`).
- **Main Flow (PROPOSED status lifecycle pending `B17`):**
  1. Branch staff view paginated orders where `processing_branch_id IN (:authorizedBranchIds)`.
  2. Authorized staff transition the order through valid forward states:
     - **Delivery orders:** `PENDING_PAYMENT → CONFIRMED → PREPARING → READY_FOR_DELIVERY → OUT_FOR_DELIVERY → COMPLETED`
     - **Pickup orders:** `PENDING_PAYMENT → CONFIRMED → PREPARING → READY_FOR_PICKUP → COMPLETED`
  3. On each transition, the backend validates the state machine rule, updates the status, and writes an order status audit entry (`BRULE-023`: actor, UTC timestamp, `fromStatus`, `toStatus`, note).
- **Exception Behavior:**
  - *E1 (Invalid state transition):* Attempting an illegal transition (e.g., `COMPLETED → PREPARING` or `READY_FOR_PICKUP` on a `DELIVERY` order) returns `400`/`409` Problem Details.
  - *E2 (Cross-branch status update):* Staff of Branch A attempt to view or transition an order assigned to Branch B; backend returns `403`/`404` (`REQ-011`, `REQ-012`).
- **Acceptance Criteria:**
  - **AC1:** Given an order assigned to Branch A, when a Branch A staff member transitions it from `CONFIRMED` to `PREPARING`, then the order status becomes `PREPARING` and an order status audit record is created (`BRULE-023`).
  - **AC2:** Given an order assigned to Branch B, when a user authorized only for Branch A attempts to read or update it, then the backend denies the request (`REQ-011`, `REQ-012`).
- **Dependencies:** `FR-006`, `FR-021`
- **Related Business Rules / Use Cases:** `BRULE-005`, `BRULE-011`, `BRULE-012`, `BRULE-013`, `BRULE-023`, `UC-BR-01`, `UC-BR-02`

### FR-023: Exception Order Reassignment Between Branches
- **Classification:** PROPOSED / NEEDS CONFIRMATION (`B14`)
- **Priority:** Should Have (Conditional on `B14`)
- **Source:** `REQ-005`, `B14`, `BRULE-005`, `BRULE-007`, `BRULE-008`, `BRULE-023`, `UC-HO-05`
- **Description & Rationale:** If an assigned processing branch encounters an unexpected issue prior to order preparation, an authorized Head Office administrator (`B14`) may reassign the order to another active branch that has sufficient stock, keeping `processingBranchId` and inventory audit records accurate (`REQ-005`, `REQ-008`).
- **Preconditions:** Order has not yet started preparation (`status` is `CONFIRMED`); target branch B has sufficient available stock; actor is `HEAD_OFFICE_ADMIN` or `SYSTEM_OWNER`.
- **Main Flow:**
  1. Administrator requests reassignment of `orderId` from Branch A to Branch B with a mandatory reason.
  2. Within one transaction, the backend releases reserved/deducted stock at Branch A (`+qty` audit record at Branch A), reserves/deducts stock at Branch B (`-qty` audit record at Branch B, verifying `available_stock >= 0`), updates `processingBranchId = Branch B` (keeping original customer price snapshot `REQ-006`), and writes a branch-reassignment audit record.
- **Acceptance Criteria:**
  - **AC1 (Pending `B14`):** Given an order assigned to Branch A, when reassigned by Head Office to Branch B with a reason, then Branch A's stock is restored (`+qty` audited), Branch B's stock is deducted (`-qty` audited), `processingBranchId` becomes Branch B (`REQ-005`), and the order's snapshotted unit prices remain unchanged (`REQ-006`).
- **Dependencies:** `FR-015`, `FR-016`, `FR-017`, `FR-021`, `FR-022`
- **Related Business Rules / Use Cases:** `BRULE-005`, `BRULE-006`, `BRULE-007`, `BRULE-008`, `BRULE-023`, `UC-HO-05`

### FR-024: Order Cancellation and Stock Restoration
- **Classification:** PROPOSED / NEEDS CONFIRMATION (`B13`, `B17`)
- **Priority:** Must Have (MVP)
- **Source:** `REQ-008`, `CLAUDE.md` §8, `B13`, `B17`, `BRULE-008`, `BRULE-023`, `WF-08`
- **Description & Rationale:** Customers (for their own orders before preparation) or authorized staff must be able to cancel an order, record the cancellation audit trail, and restore reserved/unconsumed stock to the processing branch (`REQ-008`).
- **Preconditions:** Order is in a cancellable status (e.g., `PENDING_PAYMENT` or `CONFIRMED` prior to `PREPARING`, pending `B17`).
- **Main Flow:**
  1. Authorized actor submits a cancellation request with a cancellation reason.
  2. Backend verifies actor scope (customer owns order, or staff is authorized for `processingBranchId`) and checks that current status allows cancellation.
  3. Backend updates order status to `CANCELLED`, writes an order status audit entry (`BRULE-023`), restores reserved/unconsumed stock (`+qty`) to `processingBranchId` with an `ORDER_CANCELLED` inventory audit entry (`REQ-008`), and flags the order for refund if payment was already captured (`FR-027`).
- **Exception Behavior:**
  - *E1 (Order already preparing/dispatched/completed):* Customer self-cancellation is rejected with `409 Conflict` Problem Details.
- **Acceptance Criteria:**
  - **AC1 (Pending `B17`):** Given a `CONFIRMED` order at Branch A that deducted 2 units of Product P, when cancelled with a reason, then the order status becomes `CANCELLED`, an order status audit record is written, Branch A's `available_stock` increases by `+2`, and an inventory audit record of `+2` linked to the order is written (`REQ-008`, `BRULE-023`).
- **Dependencies:** `FR-006`, `FR-017`, `FR-022`
- **Related Business Rules / Use Cases:** `BRULE-008`, `BRULE-011`, `BRULE-023`, ` BRULE-027`, `UC-CUST-07`

---

## 9. Payments and Refunds (`FR-025` to `FR-027`)

### FR-025: Server-Side Payment Webhook Verification
- **Classification:** CONFIRMED (Non-negotiable baseline rule `REQ-009`)
- **Priority:** Must Have (MVP)
- **Source:** `REQ-009`, `CLAUDE.md` §8, `B4`, `BRULE-009`, `BRULE-024`, `WF-05`
- **Description & Rationale:** Online payment notifications from a payment provider (`B4`) must be cryptographically verified on the server using the provider's official signature/HMAC verification method before any state change occurs (`REQ-009`). Browser redirects are never treated as proof of payment (`CLAUDE.md` §8).
- **Preconditions:** Payment provider sends an HTTP webhook request to the backend webhook endpoint.
- **Main Flow:**
  1. Backend receives raw webhook payload and signature headers.
  2. Backend computes/verifies the cryptographic signature using the webhook secret loaded from environment variables (`CLAUDE.md` §8).
  3. Only after signature verification succeeds does the backend pass the event to the idempotent payment processor (`FR-026`).
- **Alternative Flows:**
  - *A1 (Customer browser redirect after checkout):* When the customer's browser redirects back to the storefront return URL, the storefront queries the backend order status; the return URL endpoint never marks an order as paid unless verified by the server-side provider callback/API (`CLAUDE.md` §8).
- **Exception Behavior:**
  - *E1 (Missing, malformed, or invalid webhook signature):* Backend immediately rejects the request with `400`/`401` Problem Details, logs a security audit warning (without logging secret keys or raw card data), and performs **zero** database changes to orders, payments, or inventory (`REQ-009`).
- **Acceptance Criteria:**
  - **AC1:** Given a payment webhook request with a forged, altered, or missing signature, when sent to the webhook endpoint, then the backend returns `400` or `401` and the target order's payment status and inventory remain completely unchanged (`REQ-009`).
  - **AC2:** Given a customer hitting the browser payment return/redirect URL directly without a verified server webhook, when the order is inspected, then the order is **not** marked paid (`CLAUDE.md` §8).
- **Dependencies:** `FR-021`
- **Related Business Rules / Use Cases:** `BRULE-009`, `BRULE-024`, `UC-CUST-05`

### FR-026: Idempotent Payment Processing (Exactly-Once Guarantee)
- **Classification:** CONFIRMED (Non-negotiable baseline rule `REQ-010`)
- **Priority:** Must Have (MVP)
- **Source:** `REQ-010`, `CLAUDE.md` §6, §8, `BRULE-010`, `WF-05`
- **Description & Rationale:** The same payment or webhook event must never be processed twice (`REQ-010`). Enforced by a PostgreSQL `UNIQUE` constraint on payment provider event IDs and transaction IDs plus an idempotent service handler that tolerates retries and out-of-order delivery (`CLAUDE.md` §6, §8).
- **Preconditions:** Webhook authenticity has been verified (`FR-025`).
- **Main Flow:**
  1. Backend checks/inserts the provider's unique `provider_event_id` and `provider_transaction_id` in a transaction protected by database `UNIQUE` constraints.
  2. If the event has not been processed before, the backend records the payment, transitions the order from `PENDING_PAYMENT` to `CONFIRMED` (paid), and commits.
  3. If the same `provider_event_id` or `provider_transaction_id` arrives a second time (sequential retry or concurrent duplicate delivery), the backend catches the existing record / unique constraint, performs **no** second payment record, order transition, or inventory deduction, and returns an idempotent `200 OK` acknowledgement.
- **Exception Behavior:**
  - *E1 (Out-of-order webhook delivery):* If a late webhook arrives for an order already in a later valid state, the handler safely ignores the stale transition without corrupting order state.
- **Acceptance Criteria:**
  - **AC1:** Given a valid verified payment webhook with `eventId = "evt_1001"` and `transactionId = "txn_9001"`, when delivered twice (sequentially or concurrently), then the payment is recorded exactly once, the order status transitions once, inventory is never double-deducted, and both calls return a clean response (`REQ-010`).
  - **AC2:** Given a direct SQL attempt to insert a second payment row with a duplicate `provider_event_id` or `provider_transaction_id`, when executed against PostgreSQL, then the database `UNIQUE` constraint rejects the duplicate insert (`CLAUDE.md` §6).
- **Dependencies:** `FR-021`, `FR-025`
- **Related Business Rules / Use Cases:** `BRULE-009`, `BRULE-010`, `BRULE-026`, `UC-CUST-05`

### FR-027: Refund Recording, Processing, and Audit Trail
- **Classification:** CONFIRMED (refund audit trail `CLAUDE.md` §8); Refund execution method NEEDS CONFIRMATION (`B4`, `B20`)
- **Priority:** Must Have (MVP)
- **Source:** `CLAUDE.md` §8 ("Keep an audit trail for ... refunds"), `B4`, `B20`, `BRULE-025`, `WF-08`, `WF-12`
- **Description & Rationale:** When a paid order is cancelled (`WF-08`) or a post-delivery return/complaint is approved (`WF-12`), authorized staff must process/record the refund and persist an immutable refund audit trail (`BRULE-025`).
- **Preconditions:** Order has a verified captured payment; actor is authorized (`HEAD_OFFICE_ADMIN`, `SYSTEM_OWNER`, or `BRANCH_MANAGER` per `B5`/`B20`).
- **Main Flow:**
  1. Authorized actor submits a refund for an order specifying the refund amount (`<= captured_amount`, `BigDecimal`), refund reason, and external refund reference (or triggers provider refund if `B4`/`B20` selects automated gateway refund).
  2. Backend validates that total refunded amount for the order cannot exceed the total paid amount (`Idempotency-Key` supported).
  3. Backend updates payment/order refund status and writes an immutable refund audit entry (`BRULE-025`: actor, UTC timestamp, `orderId`, `paymentId`, `refundAmount`, reason, reference).
- **Exception Behavior:**
  - *E1 (Refund exceeds paid amount or duplicate refund):* Backend rejects with `400`/`409` Problem Details.
- **Acceptance Criteria:**
  - **AC1:** Given a paid order of `500000`, when an authorized administrator records/approves a refund of `500000` with a reason, then the refund is persisted and a refund audit entry is created with actor ID, UTC timestamp, amount, and reason (`BRULE-025`).
  - **AC2:** Given the refunded order in AC1, when a second refund is attempted that would cause total refunds to exceed `500000`, then the backend rejects the request with `409 Conflict`.
- **Dependencies:** `FR-006`, `FR-024`, `FR-026`
- **Related Business Rules / Use Cases:** `BRULE-025`, `BRULE-026`, `UC-HO-06`

---

## 10. Delivery and In-Store Pickup (`FR-028` to `FR-030`)

### FR-028: Home Delivery Dispatch and Completion Tracking
- **Classification:** CONFIRMED (`REQ-013`, `B3a`); Delivery fee, slots & rider details NEEDS CONFIRMATION (`B15`, `B17`)
- **Priority:** Must Have (MVP)
- **Source:** `REQ-005`, `REQ-011`, `REQ-013`, `B3a`, `B15`, `B17`, `WF-07`, `UC-BR-04`
- **Description & Rationale:** For orders where the customer chose home delivery (`REQ-013`), staff/delivery personnel at the processing branch dispatch the prepared order to the stored delivery address and record delivery completion.
- **Preconditions:** Order fulfilment method is `DELIVERY`, assigned to `processingBranchId`, and in `READY_FOR_DELIVERY` status.
- **Main Flow:**
  1. Authorized branch/delivery staff at `processingBranchId` mark the order `OUT_FOR_DELIVERY` (recording assigned delivery staff/courier note).
  2. Upon hand-over at the customer's delivery address, staff mark the order `COMPLETED` (and record COD payment collection if COD is enabled under `B4`).
  3. Backend writes order status audit entries for both transitions (`BRULE-023`).
- **Acceptance Criteria:**
  - **AC1:** Given a `DELIVERY` order at Branch A in `READY_FOR_DELIVERY` status, when authorized Branch A staff mark it `OUT_FOR_DELIVERY` and then `COMPLETED`, then the order stores the delivery completion timestamp and both status changes appear in the order status audit trail (`REQ-013`, `BRULE-023`).
- **Dependencies:** `FR-006`, `FR-021`, `FR-022`
- **Related Business Rules / Use Cases:** `BRULE-005`, `BRULE-011`, `BRULE-013`, `BRULE-023`, `UC-BR-04`

### FR-029: In-Store Pickup Preparation and Collection Hand-Over
- **Classification:** CONFIRMED (`REQ-013`, `B3a`); Pickup store selection rule NEEDS CONFIRMATION (`B3c`, `B15`, `B17`)
- **Priority:** Must Have (MVP)
- **Source:** `REQ-005`, `REQ-011`, `REQ-013`, `B3a`, `B3c`, `B15`, `B17`, `WF-07`, `UC-BR-03`
- **Description & Rationale:** For orders where the customer chose in-store pickup (`REQ-013`), staff at the pickup/processing branch prepare the order, mark it `READY_FOR_PICKUP`, and record collection when the customer picks it up at the store.
- **Preconditions:** Order fulfilment method is `PICKUP`, assigned to `processingBranchId`.
- **Main Flow:**
  1. Authorized staff at `processingBranchId` transition the prepared order to `READY_FOR_PICKUP`.
  2. When the customer arrives at the store and collects the order, staff verify the order reference and mark it `COMPLETED`.
  3. Backend writes order status audit records (`BRULE-023`).
- **Acceptance Criteria:**
  - **AC1:** Given a `PICKUP` order assigned to Branch A, when Branch A staff mark it `READY_FOR_PICKUP` and then `COMPLETED` upon customer collection, then the order status is `COMPLETED` and the transitions are recorded in the order status audit trail (`REQ-013`, `BRULE-023`).
- **Dependencies:** `FR-006`, `FR-021`, `FR-022`
- **Related Business Rules / Use Cases:** `BRULE-005`, `BRULE-011`, `BRULE-013`, `BRULE-022`, `BRULE-023`, `UC-BR-03`

### FR-030: Post-Delivery / Post-Pickup Customer Return and Issue Resolution
- **Classification:** PROPOSED / NEEDS CONFIRMATION (`B20`)
- **Priority:** Could Have (Conditional on `B20`)
- **Source:** P03 Business Analysis `WF-12`, `REQ-008`, `CLAUDE.md` §8, `B20`, `UC-CUST-07`, `UC-HO-06`
- **Description & Rationale:** Handles customer complaints for damaged, wrong, or wilted flowers after delivery/pickup (`WF-12`), ensuring any refund (`FR-027`) or inventory restock/spoilage (`FR-016`, `FR-019`) is properly audited.
- **Preconditions:** Order is in `COMPLETED` status.
- **Main Flow:**
  1. Customer or staff logs a return/issue request on the order with reason and notes.
  2. Authorized manager (`BRANCH_MANAGER` for their branch or `HEAD_OFFICE_ADMIN`) approves a resolution (`REFUND`, `REPLACEMENT`, or `REJECTED`).
  3. If `REFUND` is chosen, `FR-027` records the audited refund. If items are returned to the branch, staff explicitly choose whether to restock sellable items (`+qty` audited) or write off spoiled flowers (`REQ-008`).
- **Acceptance Criteria:**
  - **AC1 (Pending `B20`):** Given a completed order with an approved return, when processed, then sellable stock is never automatically incremented for wilted flowers unless staff explicitly select restock (writing a `RETURN_RESTOCK` audit entry, `REQ-008`), and any financial refund writes a refund audit record (`BRULE-025`).
- **Dependencies:** `FR-016`, `FR-022`, `FR-027`
- **Related Business Rules / Use Cases:** `BRULE-008`, `BRULE-025`, `UC-CUST-07`, `UC-HO-06`

---

## 11. Customer Management (`FR-031`)

### FR-031: Customer Profile, Saved Delivery Addresses, and Order History
- **Classification:** CONFIRMED (strict customer data isolation `CLAUDE.md` §8); Account/address book features PROPOSED / NEEDS CONFIRMATION (`B6`)
- **Priority:** Should Have (MVP)
- **Source:** `CLAUDE.md` §8, `B6`, `BRULE-027`, `UC-CUST-06`
- **Description & Rationale:** Authenticated customers can view and update their contact profile, save delivery addresses for faster checkout, and view their historical orders with snapshotted item prices (`REQ-006`).
- **Preconditions:** Customer is authenticated (`FR-002`).
- **Main Flow:**
  1. Customer views or updates their profile and delivery addresses, or views their paginated order list.
  2. Backend filters all queries strictly by the authenticated `customerId` (`BRULE-027`).
- **Acceptance Criteria:**
  - **AC1:** Given an authenticated customer, when they view their order history, then each order shows its fulfilment method (`REQ-013`), processing branch name (`REQ-005`), current status, and the snapshotted product names, SKUs, and prices from order time (`REQ-006`).
  - **AC2:** Given Customer A, when they attempt to read or edit Customer B's profile or addresses, then the backend denies the request with `403` or `404` (`BRULE-027`).
- **Dependencies:** `FR-002`, `FR-021`
- **Related Business Rules / Use Cases:** `BRULE-006`, `BRULE-027`, `UC-CUST-06`

---

## 12. Promotions and Vouchers (`FR-032`)

### FR-032: Promotional Voucher Management and Order Discount Snapshot
- **Classification:** NEEDS CONFIRMATION (`B6`)
- **Priority:** Could Have (Conditional on `B6` — out of MVP scope if `B6` declines vouchers)
- **Source:** `REQ-006`, `B6`, `UC-CUST-08`, `UC-HO-08`
- **Description & Rationale:** If vouchers/promotions are approved under `B6`, Head Office can create voucher codes (fixed amount or percentage discount, validity dates, usage limit, minimum order total) and customers can apply a valid voucher at checkout, with the discount amount preserved in the order snapshot (`REQ-006`).
- **Preconditions:** Feature approved under `B6`; voucher is active and within validity/usage limits.
- **Main Flow:**
  1. Head Office creates a voucher code.
  2. Customer applies voucher code during checkout (`FR-021`).
  3. Backend validates eligibility, calculates the discount using `BigDecimal`, snapshots the applied voucher code and discount amount on the order (`REQ-006`), and increments voucher usage atomically.
- **Acceptance Criteria:**
  - **AC1 (Pending `B6`):** Given a valid voucher applied to an order, when the voucher is later modified or expired by Head Office, then the historical order snapshot retains the exact discount and total captured at order time (`REQ-006`).
- **Dependencies:** `FR-021`
- **Related Business Rules / Use Cases:** `BRULE-006`, `UC-CUST-08`, `UC-HO-08`

---

## 13. Notifications (`FR-033`)

### FR-033: Transactional Order and Operational Notifications
- **Classification:** PROPOSED / NEEDS CONFIRMATION (`B22`)
- **Priority:** Should Have (Conditional on `B22`)
- **Source:** P03 Business Analysis §2.16, `B22`
- **Description & Rationale:** Keeps customers informed of key order lifecycle events (order placed, payment confirmed, ready for pickup / out for delivery, completed, cancelled) and alerts branch staff of new orders assigned to their branch (`REQ-005`).
- **Preconditions:** Order lifecycle event commits (`FR-021`, `FR-022`, `FR-024`, `FR-026`).
- **Main Flow:**
  1. When an order state changes, the backend publishes an internal domain event.
  2. The notification handler records an in-app notification (and dispatches email/SMS via a pluggable adapter if configured under `B22`).
- **Acceptance Criteria:**
  - **AC1 (Pending `B22`):** Given an order assigned to Branch A, when it transitions to `CONFIRMED`, then an in-app notification is visible to Branch A staff (and never to Branch B staff, `REQ-011`) and a customer confirmation notification event is recorded.
- **Dependencies:** `FR-006`, `FR-021`, `FR-022`
- **Related Business Rules / Use Cases:** `BRULE-005`, `BRULE-011`

---

## 14. Reporting (`FR-034` to `FR-035`)

### FR-034: Branch-Scoped and Chain-Wide Revenue & Order Fulfilment Reports
- **Classification:** PROPOSED / NEEDS CONFIRMATION (`B21`)
- **Priority:** Should Have (MVP)
- **Source:** `REQ-005`, `REQ-006`, `REQ-011`, `REQ-012`, `B21`, `UC-HO-07`
- **Description & Rationale:** Provides revenue and order fulfilment summaries calculated strictly from immutable order snapshots (`REQ-006`) and attributed to each order's traceable `processingBranchId` (`REQ-005`), scoped by the caller's authorized branches (`REQ-011`, `REQ-012`).
- **Preconditions:** Actor is authenticated as `SYSTEM_OWNER` / `HEAD_OFFICE_ADMIN` (all branches) or `BRANCH_MANAGER` (own assigned branch(es) only).
- **Main Flow:**
  1. Actor requests a revenue and order count summary filtered by date range (UTC) and `branchId`.
  2. Backend validates branch authorization (`REQ-011`, `REQ-012`).
  3. Backend aggregates order totals from persisted order snapshots (`REQ-006`) grouped by branch, fulfilment method (`DELIVERY` vs. `PICKUP`), and order status.
- **Exception Behavior:**
  - *E1 (Branch Manager requests another branch's report):* Backend denies with `403 Forbidden` (`REQ-011`, `REQ-012`).
- **Acceptance Criteria:**
  - **AC1:** Given completed orders across Branch A and Branch B, when a `HEAD_OFFICE_ADMIN` queries the revenue report, then revenue is calculated from the order line snapshots (`REQ-006`) and accurately attributed to Branch A and Branch B (`REQ-005`).
  - **AC2:** Given a `BRANCH_MANAGER` assigned only to Branch A, when they query the report or pass `branchId = B`, then the backend denies cross-branch access (`403`) and allows viewing only Branch A's metrics (`REQ-011`, `REQ-012`).
- **Dependencies:** `FR-006`, `FR-021`, `FR-022`
- **Related Business Rules / Use Cases:** `BRULE-005`, `BRULE-006`, `BRULE-011`, `BRULE-012`, `UC-HO-07`

### FR-035: Inventory Valuation, Low-Stock, and Spoilage Movement Summary
- **Classification:** PROPOSED / NEEDS CONFIRMATION (`B21`)
- **Priority:** Should Have (MVP)
- **Source:** `REQ-002`, `REQ-008`, `REQ-011`, `B21`, `UC-BR-05`, `UC-HO-07`
- **Description & Rationale:** Allows Branch Managers (for their branch) and Head Office (across all branches) to view current stock balances, low-stock items, and aggregated inventory movements (goods receipts, order deductions, transfers in/out, spoilage/adjustments) derived from the inventory audit trail (`REQ-008`).
- **Preconditions:** Actor is authorized for the requested `branchId`(s) (`REQ-011`, `REQ-012`).
- **Main Flow:**
  1. Actor requests inventory status and movement summary for an authorized branch and date range.
  2. Backend verifies branch authorization and returns current `available_stock` plus movement totals grouped by reason code (`REQ-008`).
- **Acceptance Criteria:**
  - **AC1:** Given inventory movements at Branch A, when the movement summary is queried for Branch A, then the net sum of audited movements (`REQ-008`) matches Branch A's current stock balance (`REQ-002`).
- **Dependencies:** `FR-006`, `FR-015`, `FR-016`
- **Related Business Rules / Use Cases:** `BRULE-002`, `BRULE-008`, `BRULE-011`, `BRULE-012`, `UC-BR-05`, `UC-HO-07`

---

## 15. Audit Logs (`FR-036`)

### FR-036: Comprehensive Security, Financial, and Operational Audit Log Querying
- **Classification:** CONFIRMED (`REQ-008`, `CLAUDE.md` §8)
- **Priority:** Must Have (MVP)
- **Source:** `REQ-008`, `CLAUDE.md` §8 ("Keep an audit trail for logins, role changes, inventory changes, price changes, order status changes and refunds"), `BRULE-008`, `BRULE-019`, `BRULE-023`, `BRULE-025`, `BRULE-030`
- **Description & Rationale:** All 6 mandatory audit categories (logins, role/branch changes, inventory changes, branch price changes, order status changes, and refunds) must be persisted immutably and be queryable by authorized administrators with pagination and filtering.
- **Preconditions:** Actor is authenticated as `SYSTEM_OWNER` or `HEAD_OFFICE_ADMIN` (or `BRANCH_MANAGER` scoped strictly to their branch's inventory/order/price audit logs).
- **Main Flow:**
  1. Authorized actor queries audit logs filtered by category, `branchId`, actor ID, entity ID, and UTC timestamp range (paginated per `CLAUDE.md` §7).
  2. Backend enforces branch-level authorization (`REQ-011`, `REQ-012`) and returns the matching immutable audit entries.
- **Acceptance Criteria:**
  - **AC1:** Given a login (`FR-001`), a role/branch change (`FR-005`), an inventory change (`FR-016`), a branch price change (`FR-013`), an order status change (`FR-022`), and a refund (`FR-027`), when each event occurs, then a corresponding immutable audit record is persisted with actor, UTC timestamp, and event details.
  - **AC2:** Given a `BRANCH_MANAGER` for Branch A, when they query branch-scoped audit trails, then they can see only Branch A entries and are denied access to Branch B entries (`REQ-011`, `REQ-012`).
- **Dependencies:** `FR-001`, `FR-005`, `FR-006`, `FR-013`, `FR-016`, `FR-022`, `FR-027`
- **Related Business Rules / Use Cases:** `BRULE-008`, `BRULE-011`, `BRULE-012`, `BRULE-019`, `BRULE-023`, `BRULE-025`, `BRULE-030`, `UC-HO-07`

---

## 16. System Settings & Conditional Walk-In POS Scope (`FR-037` to `FR-038`)

### FR-037: Chain-Wide Business Profile and Fulfilment Parameter Settings
- **Classification:** PROPOSED (`B3e`, `B7`, `B13`, `B15`)
- **Priority:** Should Have (MVP)
- **Source:** `REQ-001`, `B3e`, `B7`, `B13`, `B15`, `UC-HO-01`
- **Description & Rationale:** Allows the `SYSTEM_OWNER` / `HEAD_OFFICE_ADMIN` to view and configure chain-level operational parameters (business display name, default currency/locale `B7`, payment reservation timeout minutes `B13`, and default delivery fee/radius rules `B3e`, `B15`) without redeploying code.
- **Preconditions:** Actor is `SYSTEM_OWNER` or `HEAD_OFFICE_ADMIN`.
- **Main Flow:**
  1. Administrator updates a system setting via `/api/v1/admin/settings`.
  2. Backend validates the setting value (e.g., reservation timeout `> 0`, delivery fee `>= 0` in `BigDecimal`), persists it, and logs an audit entry.
- **Acceptance Criteria:**
  - **AC1:** Given a `SYSTEM_OWNER`, when they update a valid chain setting (such as default delivery fee or payment reservation timeout), then the setting is persisted and audited.
  - **AC2:** Given any non-Head-Office user, when they attempt to modify system settings, then the backend denies the request with `403 Forbidden`.
- **Dependencies:** `FR-001`, `FR-006`
- **Related Business Rules / Use Cases:** `BRULE-001`, `BRULE-028`, `UC-HO-01`

### FR-038: Branch Walk-In Store Sale (POS) Entry (Conditional on `B1`)
- **Classification:** NEEDS CONFIRMATION (`B1`)
- **Priority:** Could Have (Included only if Owner selects Option A in `B1`; excluded if Option B/C is chosen)
- **Source:** [walk-in-sale-scope.md](../business-analysis/processes/walk-in-sale-scope.md), `B1`, `REQ-002`, `REQ-004`, `REQ-005`, `REQ-006`, `REQ-007`, `REQ-008`, `REQ-011`, `REQ-012`, `UC-BR-10`
- **Description & Rationale:** If the owner selects **Option A** under `B1`, branch sales staff can record a physical walk-in store sale directly in the Admin App for their authorized branch, deducting branch stock (`REQ-007`, `REQ-008`) and snapshotting that branch's prices (`REQ-004`, `REQ-006`). (If **Option B** is chosen, staff use `FR-019` stock adjustments instead.)
- **Preconditions:** Feature approved under `B1`; staff user is authorized for `branchId` (`REQ-011`, `REQ-012`).
- **Main Flow:**
  1. Branch sales staff select products and quantities purchased by a walk-in customer at their branch.
  2. Backend verifies branch authorization (`REQ-011`, `REQ-012`), locks and deducts `available_stock` (`>= 0`, `REQ-007`), writes an inventory audit record (`REQ-008`), snapshots product name, SKU, and branch price (`REQ-004`, `REQ-006`), and creates a `COMPLETED` walk-in order tied to `processingBranchId = branchId` (`REQ-005`).
- **Acceptance Criteria:**
  - **AC1 (Pending `B1` Option A):** Given a walk-in sale recorded by authorized staff at Branch A, when committed, then Branch A's stock decreases (`>= 0`, `REQ-007`) with an inventory audit record (`REQ-008`), and the order stores Branch A as `processingBranchId` (`REQ-005`) with immutable price snapshots (`REQ-006`).
- **Dependencies:** `FR-006`, `FR-013`, `FR-015`, `FR-016`
- **Related Business Rules / Use Cases:** `BRULE-002`, `BRULE-004`, `BRULE-005`, `BRULE-006`, `BRULE-007`, `BRULE-008`, `BRULE-011`, `BRULE-012`, `UC-BR-10`
