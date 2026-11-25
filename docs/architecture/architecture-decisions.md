# Architecture Decision Records (ADR-0002 to ADR-0010)

- **Status:** PROPOSED (awaiting owner approval at Phase 4 gate before P06)
- **Last updated:** 2026-11-24
- **Owner:** solution-architect · **Task:** TASK-011 (Phase 4 — P05 Solution Architecture)
- **Baseline Reference:** [ADR-0001: Technology stack and repository layout](../adr/0001-technology-stack-and-repository-layout.md) (`ACCEPTED`, `DEC-001`..`DEC-008`).

This document consolidates the 9 Phase 4 Architecture Decision Records required for P05, registered as `DEC-017` through `DEC-025` in [../../project-management/DECISIONS.md](../../project-management/DECISIONS.md).

---

## ADR-0002: Modular Monolith Structure and Boundary Enforcement (`DEC-017`, resolves `T9`)

- **Status:** PROPOSED · **Date:** 2026-11-24
- **Context:** FlowerChain serves one business with 5–30 branches (`REQ-001`). Order placement (`REQ-005`, `REQ-006`, `REQ-007`, `REQ-008`, `REQ-014`) requires branch allocation, stock locking/deduction, audit logging, and order snapshot persistence in a single atomic transaction. `T9` asked how module boundaries inside the Spring Boot modular monolith should be enforced.
- **Options Considered:**
  1. **Spring Modulith + ArchUnit boundary tests** inside a single Spring Boot 3.4 application (`com.flowerchain.<module>`).
  2. **Convention-only packages** with manual code review.
  3. **Distributed Microservices** (separate deployables per domain).
- **Decision:** Choose **Option 1 (Spring Boot 3.4 Modular Monolith enforced by Spring Modulith and ArchUnit)**.
  - Direct subpackages of `com.flowerchain` are bounded modules (`shared`, `audit`, `branch`, `identity`, `customer`, `catalog`, `inventory`, `order`, `delivery`, `payment`, `notification`, `reporting`, plus conditional `promotion`).
  - Only `<module>.api` (interfaces, Java record DTOs, domain events) is public to other modules. `<module>.web`, `<module>.application`, and `<module>.internal` are inaccessible outside the module.
  - A JUnit 5 architecture test (`ApplicationModules.of(FlowerChainApplication.class).verify()`) runs on every local `./mvnw test` and CI build (no Docker required), failing the build on any cyclic dependency or illegal import.
- **Consequences:**
  - Full ACID transactions across order placement, stock reservation, and auditing (`NFR-010`).
  - Zero distributed network overhead or microservice deployment sprawl, while keeping modules cleanly decoupled.

---

## ADR-0003: PostgreSQL 16 Database Strategy, Flyway Migrations, and Schema Ownership (`DEC-018`, resolves `T10`)

- **Status:** PROPOSED · **Date:** 2026-11-24
- **Context:** `DEC-004` selected PostgreSQL and Flyway. We need to choose the target PostgreSQL major version (`T10`), primary key strategy, money/time types, and table ownership rules across modules.
- **Options Considered:**
  1. **Single PostgreSQL 16 database, single `public` schema with module-owned tables, Flyway migrations (`V<YYYYMMDDHHmm>__<description>.sql`), Hibernate `ddl-auto=validate`, `UUID` (UUIDv7/v4) primary keys, `numeric(19,2)` for money, `timestamptz` (UTC) for timestamps.**
  2. Separate PostgreSQL database instances per module.
  3. Auto-generated Hibernate schema (`ddl-auto=update`).
- **Decision:** Choose **Option 1**.
  - Target **PostgreSQL 16**.
  - Every table has an explicit owning module in `module-map.md`. No JPA entity crosses module boundaries (`@ManyToOne` across modules is forbidden; cross-module references use scalar `UUID` columns backed by SQL foreign keys).
  - Database constraints directly enforce `REQ-002`, `REQ-005` (`branch_id NOT NULL` FK), `REQ-007` (`CHECK (available_stock >= 0)`), and `REQ-010` (`UNIQUE` provider event/transaction IDs).
- **Consequences:**
  - Database-level referential integrity and check constraints back up application-layer validation (`NFR-009`).

---

## ADR-0004: Multi-Branch Data Isolation and Server-Side Authorization Guard (`DEC-019`)

- **Status:** PROPOSED · **Date:** 2026-11-24
- **Context:** `REQ-011` and `REQ-012` mandate that the backend enforces branch-level authorization and never trusts a client-supplied `branchId` without an authorization check.
- **Options Considered:**
  1. **Explicit `BranchAuthorizationGuard` in the application service layer + mandatory `branch_id IN (:authorizedBranchIds)` repository query parameters + server-resolved `FlowerChainPrincipal`.**
  2. Relying on frontend UI hiding or trusting the `branchId` query/body parameter when the user has a staff role.
  3. Separate database schemas per branch (5–30 schemas).
- **Decision:** Choose **Option 1**.
  - Separate schemas per branch (Option 3) would complicate chain-wide catalog management (`REQ-003`), automatic nearest-branch order allocation (`REQ-014`), and inter-branch stock transfers (`REQ-015`).
  - Instead, all branch-owned tables carry `branch_id NOT NULL` (`REQ-002`, `REQ-005`).
  - `FlowerChainPrincipal` resolves `globalAccess` (`SYSTEM_OWNER`, `HEAD_OFFICE_ADMIN`) and `Set<UUID> authorizedBranchIds` (`BRANCH_MANAGER`, `SALES_STAFF`, `INVENTORY_STAFF`, `DELIVERY_STAFF`) on the server on every request.
  - Every branch-scoped service method invokes `BranchAuthorizationGuard` before reading or mutating data. For inter-branch stock transfers (`REQ-015`), dispatch requires authorization for `source_branch_id` and receipt requires authorization for `target_branch_id`.
- **Consequences:**
  - Directly satisfies `REQ-011`, `REQ-012`, and `TEST-013`/`TEST-014` with clear, auditable Java code.

---

## ADR-0005: Authentication, RBAC, and Session/Token Strategy (`DEC-020`, resolves `T5`)

- **Status:** PROPOSED · **Date:** 2026-11-24
- **Context:** `T5` asked whether login should use server sessions or JWTs. FlowerChain has two distinct frontends (`frontend/storefront` for customers and `frontend/admin` for staff) and requires immediate revocation when a staff account is deactivated (`FR-004`) or when branch assignments change (`FR-005`).
- **Options Considered:**
  1. **Short-lived stateless signed JWT access token (in memory, 15 min) + server-stored revocable refresh token (`HttpOnly`, `SameSite=Lax`, backed by PostgreSQL), with server-side principal/branch-scope verification.**
  2. Long-lived stateless JWT stored in `localStorage` with roles/branches baked in for days.
  3. Pure server-side cookie session (Spring Session JDBC).
- **Decision:** Choose **Option 1** (or Option 3 where cookie-only is preferred; both resolve `authorizedBranchIds` server-side).
  - Long-lived `localStorage` JWTs (Option 2) are rejected because they expose tokens to XSS and cannot immediately revoke a deactivated employee's branch access (`FR-004`, `FR-005`).
  - Passwords are hashed with Spring Security's `DelegatingPasswordEncoder` (`{bcrypt}`).
  - All login successes, login failures, and role/branch assignment changes are written to `audit_logs` (`BRULE-030`).
- **Consequences:**
  - Compromised or deactivated staff accounts can be cut off immediately on the server.

---

## ADR-0006: Inventory Consistency, Concurrency Locking, and Nearest-Branch Allocation (`DEC-021`)

- **Status:** PROPOSED · **Date:** 2026-11-24
- **Context:** `REQ-007` requires that available inventory never becomes negative, even under concurrent checkouts for the last units of stock (`TEST-008`). `REQ-008` requires every inventory change to be audited. `REQ-014` requires automatic selection of the nearest branch with sufficient stock for delivery orders.
- **Options Considered:**
  1. **Pessimistic row-level locking (`SELECT ... FOR UPDATE` in deterministic ascending `product_id` order) + PostgreSQL `CHECK (available_stock >= 0)` + `@Version` optimistic column + same-transaction `inventory_movements` insert.**
  2. In-memory application locks (`synchronized` / `ReentrantLock` in JVM).
  3. Unlocked read-then-update in JPA.
- **Decision:** Choose **Option 1**.
  - When allocating a delivery order (`REQ-014`), `OrderService` queries candidate active branches ordered by distance to the delivery address, then acquires row locks on `(branch_id, product_id)` at the candidate branch in sorted `product_id` order.
  - If a concurrent transaction just consumed the last unit at the nearest branch, the lock check sees `available_stock < requested_qty` and seamlessly advances to the next nearest candidate branch with sufficient stock (`REQ-014` AC2) within the transaction, or rejects with `409 Conflict` if no branch has stock.
  - Every stock change inserts an immutable row into `inventory_movements` (`REQ-008`) in the same ACID transaction.
- **Consequences:**
  - Zero race-condition overselling (`REQ-007`), zero lock-order deadlocks (due to sorted `product_id` lock acquisition), and 100% audit accuracy (`REQ-008`).

---

## ADR-0007: Payment Integration, Server-Side Webhook Verification, and Idempotency (`DEC-022`)

- **Status:** PROPOSED · **Date:** 2026-11-24
- **Context:** `REQ-009` requires payment webhooks to be verified on the server, and `REQ-010` requires that the same payment is never processed twice. Specific payment providers (`B4`) may include COD / Pay at Pickup plus online gateways (e.g., VNPay, MoMo, Stripe, or VietQR).
- **Options Considered:**
  1. **Provider-agnostic `PaymentGatewayPort` adapter pattern + mandatory server-side HMAC/signature verification on raw webhook bytes (`REQ-009`) + `payment_webhook_events` deduplication table with `UNIQUE (provider, provider_event_id)` (`REQ-010`).**
  2. Trusting client browser redirects after checkout.
  3. Hardcoding a single payment provider directly inside `OrderService`.
- **Decision:** Choose **Option 1**.
  - `PaymentWebhookController` verifies the provider's cryptographic signature before parsing business effects (`REQ-009`). Invalid signatures return `401` with zero DB mutations (`TEST-011`).
  - `payment_webhook_events` and `payments` enforce database `UNIQUE` constraints on provider event IDs and transaction IDs (`REQ-010`, `TEST-012`).
  - A sandbox/mock provider implementation (`SandboxHmacPaymentGateway`) is included for automated CI integration and E2E testing, while real provider adapters (`B4`) plug into `PaymentGatewayPort`.
- **Consequences:**
  - Satisfies `REQ-009` and `REQ-010` regardless of which payment provider(s) the owner selects in `B4`.

---

## ADR-0008: Caching and Asynchronous Messaging Strategy (Deferring Redis and RabbitMQ in MVP) (`DEC-023`)

- **Status:** PROPOSED · **Date:** 2026-11-24
- **Context:** Project discussions mentioned Redis and RabbitMQ "where justified", while `CLAUDE.md` §4 mandates *"Prefer the simplest solution that meets the requirement"*.
- **Options Considered:**
  1. **PostgreSQL-first for MVP (no Redis or RabbitMQ containers required in MVP): use PostgreSQL ACID queries for stock/prices and Spring Modulith transactional domain events for internal async notifications.**
  2. Mandatory Redis cache for inventory/prices and RabbitMQ broker between modules inside the monolith.
- **Decision:** Choose **Option 1**.
  - Caching branch inventory or branch prices in Redis creates stale-cache bugs that conflict with `REQ-007` (real-time non-negative stock) and `REQ-004`/`REQ-006` (accurate branch price snapshots). At 5–30 branches, indexed PostgreSQL queries easily meet `NFR-001` (`p95 <= 300 ms`).
  - Inside a single Modular Monolith, publishing domain events via Spring's `ApplicationEventPublisher` / Spring Modulith event publication table provides transactional decoupling without operating a RabbitMQ cluster.
- **Consequences:**
  - Simpler local development, faster CI Testcontainers runs (`DEC-015`), and fewer moving parts in production. Redis or RabbitMQ can be introduced via a new ADR if future scale requires distributed caching or external system messaging.

---

## ADR-0009: Product Image and Static Asset Storage Strategy (`DEC-024`)

- **Status:** PROPOSED · **Date:** 2026-11-24
- **Context:** The central product catalog (`REQ-003`, `FR-010`) displays product photos on the storefront.
- **Options Considered:**
  1. **Store external/CDN image URLs on `products` + optional `FileStoragePort` abstraction (local filesystem/volume in dev/CI, S3-compatible object storage in production).**
  2. Storing binary image BLOBs inside PostgreSQL tables.
- **Decision:** Choose **Option 1**.
  - PostgreSQL stores only image URLs/paths and metadata, keeping database backups fast and compact (`NFR-014`).
  - If direct image upload from the Admin App is used, it writes through a `FileStoragePort` interface (validating MIME type `image/jpeg`, `image/png`, `image/webp` and max file size `<= 5 MB`).
- **Consequences:**
  - Fast storefront page loads (`NFR-002`) and lightweight PostgreSQL backups.

---

## ADR-0010: CI-First Verification (`DEC-015`) and Containerized Deployment Topology (`DEC-025`, resolves `T11`, `T12`)

- **Status:** PROPOSED · **Date:** 2026-11-24
- **Context:** Per `DEC-015`, local Docker is not required on developer machines; Testcontainers integration tests and Docker image builds run in GitHub Actions CI. We also need decisions for `T11` (frontend unit test tool) and `T12` (how developers run or test locally without Docker).
- **Options Considered:**
  1. **CI runs full Testcontainers PostgreSQL 16 + Playwright E2E + multi-stage Docker builds on every push/PR; locally, developers run fast unit/slice/ArchUnit tests (`./mvnw test`) and Vitest (`npm test`), and can optionally connect `spring-boot:run` to a portable local PostgreSQL 16 binary or remote dev DB via environment variables (`T12`).**
  2. Using H2 in-memory database locally (explicitly forbidden by `CLAUDE.md` §9).
- **Decision:** Choose **Option 1**.
  - **H2 is strictly forbidden** (`CLAUDE.md` §9).
  - Resolve **`T11`**: Adopt **Vitest + React Testing Library** for frontend unit and component tests alongside Playwright (`DEC-006`) for E2E tests.
  - Resolve **`T12`**: Local unit, service, controller (`@WebMvcTest`), and Spring Modulith/ArchUnit boundary tests run via `./mvnw test` with zero database or Docker required; full PostgreSQL 16 Testcontainers integration tests (`*IT.java`) run automatically in GitHub Actions CI (and locally whenever a developer has Docker or a local PostgreSQL 16 instance available).
  - Production/staging deployment packages 3 containers behind Nginx: `flowerchain-nginx` (serving `storefront` and `admin` static assets + `/api/v1` reverse proxy), `flowerchain-backend` (Java 21 JRE non-root container), and managed/containerized **PostgreSQL 16**.
- **Consequences:**
  - Respects `DEC-015` (no local Docker obligation) and `CLAUDE.md` §9 (no H2 stand-ins), while ensuring every migration, constraint, and security rule is verified against real PostgreSQL 16 in CI.
