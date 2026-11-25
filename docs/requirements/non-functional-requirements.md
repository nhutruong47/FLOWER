# Non-Functional Requirements Specification (`NFR-001` to `NFR-015`)

- **Status:** DRAFT (awaiting owner approval at Phase 3 gate before P05)
- **Last updated:** 2026-11-24
- **Owner:** business-analyst (`requirements-engineering` skill) · **Task:** TASK-010 (Phase 3 — P04 Requirements Engineering)
- **Sources:** `CLAUDE.md` §3–9, `ADR-0001`, `DEC-001` to `DEC-016`, Project Rules Brief (2026-11-24).
- **Classification Legend:**
  - **CONFIRMED:** Explicitly mandated in `CLAUDE.md`, `DECISIONS.md`, or owner baseline rules.
  - **PROPOSED:** Measurable engineering target proposed for the 5–30 branch scale; requires owner approval.

---

## 1. Performance (`NFR-001` to `NFR-002`)

### NFR-001: API Response Latency and Pagination Limits
- **Classification:** CONFIRMED (pagination mandatory `CLAUDE.md` §7); Latency targets **PROPOSED**
- **Priority:** Must Have (MVP)
- **Source:** `CLAUDE.md` §7 ("List endpoints are paginated, with a maximum page size")
- **Description & Rationale:** Ensures fast storefront browsing and responsive branch order/inventory operations across 5–30 branches while preventing unbounded queries.
- **Measurable Targets & Acceptance Criteria:**
  - **AC1 (CONFIRMED):** Every list endpoint under `/api/v1/**` enforces pagination with a default page size (e.g., `20`) and a hard maximum page size of `100`; requests asking for `size > 100` are rejected with `400 Bad Request` or clamped to `100`.
  - **AC2 (PROPOSED):** Under a baseline load of 30 branches and 50 concurrent users, 95th-percentile (`p95`) server response time is `<= 300 ms` for catalog/inventory read endpoints and `<= 500 ms` for order placement and stock transfer write endpoints (excluding external payment provider network latency).

### NFR-002: Frontend Bundle and Page Load Performance
- **Classification:** **PROPOSED**
- **Priority:** Should Have (MVP)
- **Source:** `DEC-005` (two separate Vite React apps: `storefront` and `admin`)
- **Description & Rationale:** Separating `frontend/storefront` from `frontend/admin` (`ADR-0001`) keeps internal admin code out of the customer bundle.
- **Measurable Targets & Acceptance Criteria:**
  - **AC1 (CONFIRMED):** The built `frontend/storefront` production bundle contains zero imports or routes from `frontend/admin` (`CLAUDE.md` §4).
  - **AC2 (PROPOSED):** Initial storefront main JavaScript bundle is `<= 250 KB` gzipped.

---

## 2. Availability and Reliability (`NFR-003` to `NFR-004`)

### NFR-003: Service Availability and Peak Floral Holiday Readiness
- **Classification:** **PROPOSED**
- **Priority:** Should Have (MVP)
- **Source:** P03 Business Analysis §1, §2.4 (5–30 branches; floral retail peaks)
- **Description & Rationale:** Flower shops experience concentrated order spikes on floral holidays (Valentine's Day, International Women's Day, Vietnamese Women's Day 20/10, Teachers' Day 20/11, Tet).
- **Measurable Targets & Acceptance Criteria:**
  - **AC1 (PROPOSED):** Target production uptime is `>= 99.5%` monthly during standard operations and `>= 99.9%` during scheduled peak floral holidays, with health readiness probes exposed at `/actuator/health` for automated container health checks.

### NFR-004: Idempotency and Fault-Tolerant Webhook & Order Retries
- **Classification:** **CONFIRMED**
- **Priority:** Must Have (MVP)
- **Source:** `REQ-009`, `REQ-010`, `CLAUDE.md` §7, §8, `BRULE-010`, `BRULE-026`
- **Description & Rationale:** Network timeouts between clients, payment gateways, and the server must never cause duplicate orders, double charges, or double inventory deductions.
- **Measurable Targets & Acceptance Criteria:**
  - **AC1 (CONFIRMED):** Payment webhook processing is 100% idempotent: delivering the same provider event ID or transaction ID `N` times (`N >= 2`) results in exactly `1` processed payment and `1` state transition (`REQ-010`).
  - **AC2 (CONFIRMED):** Retrying order creation or payment initiation with the same `Idempotency-Key` header within 24 hours returns the original outcome without duplicating records (`CLAUDE.md` §7).

---

## 3. Scalability (`NFR-005`)

### NFR-005: Multi-Branch Operational Scale (5–30 Branches)
- **Classification:** **CONFIRMED** (5–30 branch target `CLAUDE.md` §1); Throughput numbers **PROPOSED**
- **Priority:** Must Have (MVP)
- **Source:** `REQ-001`, `CLAUDE.md` §1, `ADR-0001`
- **Description & Rationale:** The modular monolith and PostgreSQL schema must support 5–30 active branches, central catalog items, per-branch price matrices, and branch inventory without cross-branch lock contention.
- **Measurable Targets & Acceptance Criteria:**
  - **AC1 (CONFIRMED):** Stock locking for an order or transfer at Branch A locks only Branch A's inventory rows for the affected SKUs and never blocks concurrent orders at Branch B (`REQ-002`, `CLAUDE.md` §6).
  - **AC2 (PROPOSED):** The database schema and indexes support at least `50` branches, `5,000` catalog SKUs (`250,000` branch-price/inventory rows), and `10,000` orders per day without full-table sequential scans on branch-filtered queries (verified via index coverage on `branch_id` foreign keys).

---

## 4. Security (`NFR-006` to `NFR-008`)

### NFR-006: Deny-by-Default Access Control and Branch Data Isolation
- **Classification:** **CONFIRMED**
- **Priority:** Must Have (MVP)
- **Source:** `REQ-011`, `REQ-012`, `CLAUDE.md` §8, `BRULE-011`, `BRULE-012`, `BRULE-027`, `BRULE-028`
- **Description & Rationale:** Prevents unauthorized access and cross-branch privilege escalation.
- **Measurable Targets & Acceptance Criteria:**
  - **AC1 (CONFIRMED):** 100% of API endpoints are protected by a deny-by-default Spring Security filter chain (`CLAUDE.md` §8).
  - **AC2 (CONFIRMED):** 100% of branch-owned endpoints and service methods validate target `branchId` against the authenticated user's server-resolved branch assignments (`REQ-011`, `REQ-012`), verified by automated negative integration tests (`CLAUDE.md` §9).
  - **AC3 (CONFIRMED):** Zero occurrences of `dangerouslySetInnerHTML` with user or server data in the React frontends (`CLAUDE.md` §5), and 100% parameterized SQL/JPA queries (`CLAUDE.md` §8).

### NFR-007: Credential, Secret, and Payment Data Protection
- **Classification:** **CONFIRMED**
- **Priority:** Must Have (MVP)
- **Source:** `REQ-009`, `CLAUDE.md` §5, §8, `BRULE-009`, `BRULE-024`, `BRULE-029`
- **Description & Rationale:** Protects user passwords, API secrets, and payment data.
- **Measurable Targets & Acceptance Criteria:**
  - **AC1 (CONFIRMED):** 100% of stored user passwords use `DelegatingPasswordEncoder` (`bcrypt` or stronger).
  - **AC2 (CONFIRMED):** Zero secrets, `.env` files, private keys, or payment credentials are committed to Git (`.gitignore` + `protect-secrets.sh` hook passing 27/27 tests).
  - **AC3 (CONFIRMED):** Zero raw card numbers (PAN/CVV), passwords, tokens, or full payment webhook payloads are stored in the database or written to application logs (`CLAUDE.md` §5, §8).

### NFR-008: RFC 9457 Problem Details Error Handling Without Stack Trace Leakage
- **Classification:** **CONFIRMED**
- **Priority:** Must Have (MVP)
- **Source:** `CLAUDE.md` §7, §8
- **Description & Rationale:** Standardizes API error responses for both frontends while preventing internal stack traces or database details from leaking to clients.
- **Measurable Targets & Acceptance Criteria:**
  - **AC1 (CONFIRMED):** 100% of API error responses (`4xx` and `5xx`) use `application/problem+json` (RFC 9457 Problem Details) and never include Java stack traces, SQL queries, or internal class names in the response body (`CLAUDE.md` §7, §8).

---

## 5. Data Integrity (`NFR-009` to `NFR-010`)

### NFR-009: Concurrency Safety and Database-Enforced Business Invariants
- **Classification:** **CONFIRMED**
- **Priority:** Must Have (MVP)
- **Source:** `REQ-002`, `REQ-005`, `REQ-006`, `REQ-007`, `REQ-008`, `REQ-010`, `CLAUDE.md` §5, §6
- **Description & Rationale:** Business invariants must be guaranteed at both the application transaction layer and the PostgreSQL constraint layer.
- **Measurable Targets & Acceptance Criteria:**
  - **AC1 (CONFIRMED):** All monetary columns use PostgreSQL `numeric` with explicit precision and Java `BigDecimal` (never `float` or `double`), and all timestamps use UTC `timestamptz` / `java.time` (`CLAUDE.md` §5, §6).
  - **AC2 (CONFIRMED):** PostgreSQL enforces:
    1. `branch_id NOT NULL` foreign keys on all branch-owned tables (`REQ-002`, `REQ-005`),
    2. `CHECK (available_stock >= 0)` on branch inventory (`REQ-007`),
    3. `UNIQUE` constraints on payment provider event and transaction IDs (`REQ-010`), and
    4. Immutable snapshot columns (`product_name`, `sku`, `unit_price`) on order lines (`REQ-006`).
  - **AC3 (CONFIRMED):** Flyway manages 100% of schema migrations (`V<YYYYMMDDHHmm>__<short_description>.sql`), and Hibernate `ddl-auto` is set strictly to `validate` (or `none`) (`CLAUDE.md` §6).

### NFR-010: Atomic Transactional Audit Completeness
- **Classification:** **CONFIRMED**
- **Priority:** Must Have (MVP)
- **Source:** `REQ-008`, `CLAUDE.md` §6, §8
- **Description & Rationale:** Audit logs must never drift out of sync with the state changes they record.
- **Measurable Targets & Acceptance Criteria:**
  - **AC1 (CONFIRMED):** Inventory audit records (`REQ-008`), price-change audit records, order status audit records, role-change audit records, and refund audit records are committed in the **same** ACID database transaction as the underlying mutation.

---

## 6. Maintainability and Testing (`NFR-011` to `NFR-012`)

### NFR-011: Modular Monolith Boundary Enforcement
- **Classification:** **CONFIRMED** (`DEC-001`, `CLAUDE.md` §4)
- **Priority:** Must Have (MVP)
- **Source:** `DEC-001`, `ADR-0001`, `CLAUDE.md` §4, `T9`
- **Description & Rationale:** Keeps the single deployable Spring Boot application modular, decoupled, and free of cyclic dependencies.
- **Measurable Targets & Acceptance Criteria:**
  - **AC1 (CONFIRMED):** Every business module under `com.flowerchain.<module>` communicates with other modules exclusively via `<module>.api` interfaces/DTOs or domain events; zero cross-module imports of entities, repositories, or internal classes exist (`CLAUDE.md` §4).
  - **AC2 (PROPOSED — `T9`):** An automated architecture boundary test (ArchUnit or Spring Modulith verification test) runs in every Maven build and fails the build on any cyclic dependency or illegal cross-module package access.

### NFR-012: Real-PostgreSQL Integration Testing and Baseline Test Coverage
- **Classification:** **CONFIRMED** (`DEC-006`, `DEC-015`, `CLAUDE.md` §9)
- **Priority:** Must Have (MVP)
- **Source:** `DEC-006`, `DEC-015`, `CLAUDE.md` §9
- **Description & Rationale:** Proves that migrations, constraints, locking, and security rules work against real PostgreSQL rather than in-memory stand-ins.
- **Measurable Targets & Acceptance Criteria:**
  - **AC1 (CONFIRMED):** Integration tests run against real PostgreSQL via Testcontainers in GitHub Actions CI (never H2 or in-memory databases) (`CLAUDE.md` §9, `DEC-015`).
  - **AC2 (CONFIRMED):** Dedicated automated tests exist and pass for all 6 mandatory baseline scenarios in `CLAUDE.md` §9 / `docs/testing/TEST_CASES.md` (`REQ-006` through `REQ-012`).

---

## 7. Usability and Accessibility (`NFR-013`)

### NFR-013: Responsive UI, Clear Error Feedback, and Baseline Accessibility
- **Classification:** **PROPOSED** (conventions in `.claude/agents/frontend-developer.md` & `CLAUDE.md` §5)
- **Priority:** Should Have (MVP)
- **Source:** `.claude/agents/frontend-developer.md`, `.claude/skills/frontend-development/SKILL.md`
- **Description & Rationale:** Customers shop primarily on mobile and desktop browsers, while branch staff use desktop/tablet screens in busy flower shops.
- **Measurable Targets & Acceptance Criteria:**
  - **AC1 (CONFIRMED):** Every screen in `frontend/storefront` and `frontend/admin` explicitly handles `loading`, `empty`, `error` (parsing RFC 9457 Problem Details), and `unauthorized` states, using semantic HTML, associated `<label>` elements on all form inputs, and keyboard-reachable controls.
  - **AC2 (PROPOSED):** Storefront pages render without horizontal scrolling across viewport widths from `360px` (mobile) to `1440px` (desktop).

---

## 8. Backup, Recovery, and Observability (`NFR-014` to `NFR-015`)

### NFR-014: Database Backup, Restore Runbook, and Migration Safety
- **Classification:** **PROPOSED** (runbook requirement CONFIRMED in `CLAUDE.md` §11 / `devops-engineer.md`)
- **Priority:** Should Have (Phase 11/12)
- **Source:** `CLAUDE.md` §6, §11, `.claude/agents/devops-engineer.md`
- **Description & Rationale:** Protects orders, payment records, and audit trails against data loss.
- **Measurable Targets & Acceptance Criteria:**
  - **AC1 (CONFIRMED):** Documented and tested backup and restore runbooks exist in `docs/deployment/runbooks/` before production readiness (Phase 12), and merged Flyway migrations are never edited or destructively rolled back on shared databases.
  - **AC2 (PROPOSED):** Target Recovery Point Objective (`RPO`) `<= 24 hours` (daily automated PostgreSQL backups + WAL archiving where supported by hosting target `T6`) and Recovery Time Objective (`RTO`) `<= 2 hours`.

### NFR-015: Structured Logging and Request Correlation Observability
- **Classification:** **CONFIRMED** (SLF4J + secret redaction `CLAUDE.md` §5); Correlation header **PROPOSED**
- **Priority:** Should Have (MVP)
- **Source:** `CLAUDE.md` §5 ("Log with SLF4J. Never log passwords, tokens, secrets or full payment payloads.")
- **Description & Rationale:** Enables troubleshooting of multi-branch orders and payment webhooks without exposing sensitive data.
- **Measurable Targets & Acceptance Criteria:**
  - **AC1 (CONFIRMED):** All backend logging uses SLF4J with zero `System.out.println` calls and zero sensitive values logged (`CLAUDE.md` §5).
  - **AC2 (PROPOSED):** Every HTTP request is assigned a correlation ID (`X-Request-Id` / MDC trace ID) included in application logs and RFC 9457 Problem Details error responses.
