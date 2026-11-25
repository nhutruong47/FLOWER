# Technology Stack Evaluation and Architecture Style

- **Status:** PROPOSED (awaiting owner approval at Phase 4 gate before P06)
- **Last updated:** 2026-11-24
- **Owner:** solution-architect · **Task:** TASK-011 (Phase 4 — P05 Solution Architecture)
- **Sources:** `CLAUDE.md` §3–8, `ADR-0001`, `DECISIONS.md` (`DEC-001` to `DEC-016`), P03 Business Analysis, P04 Requirements (`FR-001`..`038`, `NFR-001`..`015`).

---

## 1. Technology Stack Evaluation

No existing technologies from `ADR-0001` or `DEC-001`..`DEC-016` are replaced. Every technology is evaluated against the 5–30 branch scale (`REQ-001`, `NFR-005`), the local toolchain (`JDK 21`, `Node.js LTS`, `DEC-014`), and the CI-only Testcontainers constraint (`DEC-015`).

### 1.1 Backend Stack

| Component | Recommended Version / Pin | Compatibility & Fit | Trade-offs & Justification | Status |
|---|---|---|---|---|
| **Java** | **Java 21 LTS** (Eclipse Temurin / OpenJDK 21) | Verified locally (`21.0.13.1` / `21.0.11`) and in GitHub Actions (`actions/setup-java@v4`). | Virtual threads, records (for immutable API DTOs), pattern matching, long-term support. | **CONFIRMED** (`DEC-002`) |
| **Spring Boot** | **Spring Boot 3.4.x** (`Spring MVC`, `Spring Security`, `Spring Data JPA`, `Validation`, `Actuator`) | Native Java 21 support, Jakarta EE 10, RFC 9457 `ProblemDetail` built into Spring MVC. | Synchronous Spring MVC is simpler to debug, trace, and lock transactionally with JPA/PostgreSQL than WebFlux reactive stacks. | **CONFIRMED** (`DEC-002`) |
| **Module Boundary Enforcement (`T9`)** | **Spring Modulith 1.3.x** (`spring-modulith-starter-core`, `spring-modulith-starter-test`) + **ArchUnit** | Built by the Spring team specifically for Spring Boot 3.x Modular Monoliths. | Verifies acyclic module dependencies and blocks cross-module access to internal packages (`domain`, `repository`, `service`) during `./mvnw test` without requiring Docker (`DEC-015`). | **PROPOSED** (`DEC-017` / `ADR-0002`, resolves `T9`) |
| **Build Tool** | **Maven 3.9.x via Maven Wrapper (`mvnw` / `mvnw.cmd`)** | Zero global Maven installation needed; LF line endings enforced for `mvnw` via `.gitattributes`. | Standard enterprise Java build lifecycle; deterministic dependency locking. | **CONFIRMED** (`DEC-003`) |

### 1.2 Database & Persistence Stack

| Component | Recommended Version / Pin | Compatibility & Fit | Trade-offs & Justification | Status |
|---|---|---|---|---|
| **PostgreSQL (`T10`)** | **PostgreSQL 16 (alpine/official image in CI)** | Full support for `timestamptz`, `numeric(19,2)`, `CHECK` constraints, `SELECT ... FOR UPDATE` row locks, and `UNIQUE` indexes. | Strong ACID guarantees required for non-negative branch inventory (`REQ-007`), atomic audit trails (`REQ-008`, `NFR-010`), and idempotent webhooks (`REQ-010`). | **CONFIRMED** (`DEC-004`); v16 **PROPOSED** (`T10`) |
| **Flyway** | **Flyway Core + `flyway-database-postgresql`** (managed by Spring Boot 3.4 BOM) | Runs automatically on startup; paired with `spring.jpa.hibernate.ddl-auto=validate`. | Guarantees schema reproducibility and prevents accidental schema drift (`CLAUDE.md` §6, `NFR-009`). | **CONFIRMED** (`DEC-004`) |

### 1.3 Frontend Stack

| Component | Recommended Version / Pin | Compatibility & Fit | Trade-offs & Justification | Status |
|---|---|---|---|---|
| **React + TypeScript** | **React 19.x** (or **18.3.x** LTS), **TypeScript 5.6+ (`strict: true`)** | Runs on installed Node.js LTS (`DEC-014`). | Strict typing between backend OpenAPI DTOs (`frontend/packages/shared`) and both UI apps (`frontend/storefront` and `frontend/admin`). | **CONFIRMED** (`DEC-005`) |
| **Build & Styling** | **Vite 6.x**, **Tailwind CSS 3.4.x / 4.x** | Fast HMR and isolated production builds per workspace app. | Two separate Vite apps prevent internal admin routes/components from ever shipping in the customer storefront bundle (`ADR-0001`, `NFR-002`). | **CONFIRMED** (`DEC-005`) |
| **Frontend Unit/Component Test Runner (`T11`)** | **Vitest + React Testing Library** (alongside **Playwright** for E2E) | Native Vite integration (shares `vite.config.ts`), zero Docker needed. | Fast unit/component test feedback locally while Playwright (`DEC-006`) covers full end-to-end journeys. | **PROPOSED** (`DEC-025`, resolves `T11`) |

### 1.4 Infrastructure, Caching, Messaging & Hosting Evaluation

| Component | Architectural Evaluation for 5–30 Branches | Recommendation | Status |
|---|---|---|---|
| **Docker & GitHub Actions** | Multi-stage Dockerfiles in `infra/`; GitHub Actions Linux runners execute `mvnw verify` (including Testcontainers PostgreSQL 16) and frontend builds. | Mandatory per `DEC-007` and `DEC-015` (no local Docker). | **CONFIRMED** (`DEC-007`, `DEC-015`) |
| **Redis (where justified)** | At 5–30 branches (`<= 250,000` branch-price/stock rows), PostgreSQL primary-key/index lookups take `< 2 ms` and row locking (`SELECT ... FOR UPDATE`) provides strict ACID consistency without cache-invalidation race conditions (`REQ-007`). Introducing Redis in MVP would risk stale branch stock/prices and require an extra container. | **Defer Redis for MVP (`ADR-0008`).** Use Spring Cache abstraction (`@Cacheable`) backed by in-memory cache only for static public branch list if needed, keeping a clean seam to plug in Redis later if traffic exceeds single-node capacity. | **PROPOSED** (`ADR-0008`, `DEC-022`) |
| **RabbitMQ (where justified)** | Within a single Modular Monolith (`DEC-001`), cross-module events (e.g., `OrderConfirmedEvent` → Notification module) can be handled reliably via **Spring Modulith's transactional event publication log (`@ApplicationModuleListener`)** backed by PostgreSQL, with zero external broker overhead. | **Defer RabbitMQ for MVP (`ADR-0002`, `ADR-0008`).** Use Spring application events / transactional outbox inside PostgreSQL. Add RabbitMQ only if external microservices are approved in the future. | **PROPOSED** (`ADR-0008`, `DEC-022`) |
| **Nginx & External Hosting (`T6`)** | Nginx serves the static Vite builds (`frontend/storefront` and `frontend/admin`) and reverse-proxies `/api/v1/**` to the Spring Boot container with TLS termination, rate limiting, and security headers. | Containerized Nginx + Spring Boot + managed PostgreSQL 16 (`ADR-0010`). Exact cloud provider remains open (`T6`). | **PROPOSED** (`ADR-0010`, `DEC-024`, `T6`) |

---

## 2. Architecture Style: Modular Monolith

### 2.1 Why Modular Monolith Fits FlowerChain (`DEC-001`, `ADR-0002`)
1. **ACID Transactional Integrity (`REQ-006`, `REQ-007`, `REQ-008`, `NFR-010`):** Placing an order requires allocating the nearest branch with stock (`REQ-014`), locking and deducting branch stock (`REQ-007`), writing the inventory audit record (`REQ-008`), and saving the order with product/price snapshots (`REQ-006`) in **one atomic PostgreSQL transaction**. Microservices would turn this into a fragile distributed saga.
2. **Operational Simplicity for 5–30 Branches:** One backend deployable (`backend/`) and one PostgreSQL database dramatically reduce hosting cost, latency, and deployment complexity while preserving strict module boundaries.
3. **Future Extraction Seam:** Because modules communicate only through `<module>.api` interfaces and immutable DTO records or domain events, any module can be extracted later if business scale ever demands it.

### 2.2 System Boundaries
- **Inside the FlowerChain System Boundary:**
  - `frontend/storefront` (Customer SPA)
  - `frontend/admin` (Head Office & Branch Staff SPA)
  - `frontend/packages/shared` (Compile-time shared TypeScript package)
  - `backend` (Spring Boot 3.4 Modular Monolith)
  - `PostgreSQL 16` (Single relational database with module-owned tables)
- **External Systems (Integration Boundaries):**
  - **Payment Provider Gateway (`B4`):** External checkout redirect / QR generation + inbound signed HTTPS webhook (`/api/v1/webhooks/payments/{provider}`).
  - **Notification Delivery Providers (`B22`):** Outbound SMTP/Email or SMS/Zalo gateway accessed behind a port interface (`NotificationSenderPort`).

### 2.3 Internal Package & Layer Structure per Module

Every business module under `com.flowerchain.<module>` follows a consistent 4-package layout:

```text
com.flowerchain.<module>
├── api/                  # PUBLIC: Exposed to other modules (and/or web layer)
│   ├── <Module>Api.java          # Public interface other modules may call
│   ├── dto/                      # Immutable Java record DTOs for cross-module & REST contracts
│   └── event/                    # Immutable domain event records published by this module
├── web/                  # INTERNAL: Thin REST controllers
│   ├── Storefront<Module>Controller.java   # /api/v1/storefront/...
│   └── Admin<Module>Controller.java        # /api/v1/admin/...
├── application/          # INTERNAL: Use-case orchestration & @Transactional boundaries
│   └── <Module>Service.java
└── internal/             # INTERNAL: Domain entities, repositories, and business logic
    ├── domain/                   # JPA entities & rich domain value objects / state machines
    └── repository/               # Spring Data JPA repositories (package-private)
```

*(Cross-cutting infrastructure that contains no business rules lives in `com.flowerchain.shared`: RFC 9457 `GlobalExceptionHandler`, `BranchSecurityContext`, pagination records, `IdempotencyFilter`, and `Clock` configuration.)*

### 2.4 Layer Responsibilities & SOLID Principles

1. **Web Layer (`web/`):**
   - Thin `@RestController` classes under `/api/v1/storefront/**` or `/api/v1/admin/**`.
   - Validates untrusted input with Jakarta Bean Validation (`@Valid`), extracts the authenticated principal/branch context, delegates immediately to an `application/` service, and returns typed Java `record` DTOs.
   - **Never** contains business logic, `@Transactional` annotations, or direct repository calls (`CLAUDE.md` §4, §5).
2. **Application Layer (`application/`):**
   - Owns `@Transactional` boundaries (`CLAUDE.md` §5).
   - Enforces branch-level authorization (`REQ-011`, `REQ-012`) via `BranchAuthorizationGuard`.
   - Coordinates domain entities, calls other modules exclusively through their `<other>.api.<Other>Api` interface (Dependency Inversion Principle), and publishes domain events (`ApplicationEventPublisher`).
3. **Domain & Persistence Layer (`internal/domain/`, `internal/repository/`):**
   - JPA entities encapsulate domain invariants (e.g., `BranchInventory.deduct(quantity)` throws `InsufficientStockException` if `availableStock < quantity`; `Order.transitionTo(nextStatus)` enforces the valid status state machine).
   - Repositories are package-private interfaces extending `JpaRepository`, using parameterized queries and branch-scoped finder methods (`findByBranchIdIn(...)`).
   - **Entities never leave their owning module** (`CLAUDE.md` §5).

### 2.5 Dependency & Communication Rules
- **Rule 1 (Public API Only):** Module A may only import types from `com.flowerchain.<moduleB>.api..*` or `com.flowerchain.shared..*`. Importing `com.flowerchain.<moduleB>.internal..*`, `application..*`, or `web..*` fails the ArchUnit / Spring Modulith build test (`NFR-011`).
- **Rule 2 (No Cycles):** Module dependency graph is a strict Directed Acyclic Graph (DAG). Where a lower-level module would otherwise need to notify a higher-level module, it publishes a domain event or implements an interface port.
- **Rule 3 (No Cross-Module Foreign Key Joins in JPA):** A module's JPA entity references another module's entity by value ID (`UUID branchId`, `UUID productId`), never by a cross-module `@ManyToOne` JPA entity association. Database-level foreign keys in PostgreSQL still enforce referential integrity (`REQ-002`, `REQ-005`).

### 2.6 DTO, Money, and Time Conventions (`DEC-010` Confirmed)
- **DTOs:** Immutable Java `record` classes with Jakarta Bean Validation (`@NotNull`, `@NotBlank`, `@Positive`, `@PositiveOrZero`, `@Size`). Explicit mapper methods convert between domain entities and DTOs inside the module.
- **Money:** Java `BigDecimal` (scale and rounding mode explicitly defined) mapped to PostgreSQL `numeric(19, 2)` (`CLAUDE.md` §5, §6).
- **Time:** `java.time.Instant` (stored in PostgreSQL `timestamptz` in UTC and serialized as ISO-8601 UTC strings, e.g., `2026-11-24T08:30:00Z`).

### 2.7 Transaction Boundaries
- **Single-Transaction ACID Rule (`NFR-010`):**
  - Every state-changing use case executes inside one `@Transactional` service method on the primary PostgreSQL data source.
  - The business mutation (e.g., updating `branch_inventories`, `branch_prices`, `orders`, or `payments`) and its mandatory audit record (`inventory_movements`, `audit_logs`) commit or roll back **together in the same database transaction**.
- **Read-Only Transactions:** Query methods use `@Transactional(readOnly = true)` for performance optimization.

### 2.8 Error Handling Strategy (RFC 9457 Problem Details)
- Centralized `@RestControllerAdvice` (`GlobalExceptionHandler`) maps all exceptions to `application/problem+json` (`org.springframework.http.ProblemDetail`, RFC 9457) (`CLAUDE.md` §7, `NFR-008`):
  - `400 Bad Request`: Bean Validation failures (includes `errors` map of field-level diagnostics) or malformed requests.
  - `401 Unauthorized`: Unauthenticated call or invalid webhook signature (`REQ-009`).
  - `403 Forbidden`: Authenticated user lacks role or branch authorization (`REQ-011`, `REQ-012`).
  - `404 Not Found`: Resource does not exist **or** caller is not allowed to know the resource exists (e.g., Customer A requesting Customer B's order, `CLAUDE.md` §7).
  - `409 Conflict`: Business state conflict (insufficient stock `REQ-007`, invalid order status transition, concurrent optimistic lock failure, duplicate refund).
  - `422 Unprocessable Entity`: Delivery address outside all branch delivery zones (`REQ-014`).
  - `500 Internal Server Error`: Generic safe error message with `X-Request-Id` correlation ID; stack traces are logged via SLF4J on the server and **never** returned in the JSON response (`CLAUDE.md` §7, `NFR-008`).
