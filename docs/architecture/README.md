# Architecture

- **Owner:** solution-architect · **Phase:** 4 (P05 Solution Architecture) · **Status:** PROPOSED (complete P05 architecture package awaiting owner approval before P06).
- **Last updated:** 2026-11-24

This folder holds the comprehensive P05 Solution Architecture deliverables for FlowerChain:

1. **[technology-evaluation-and-architecture-style.md](technology-evaluation-and-architecture-style.md):**
   - Evaluation of the confirmed stack (Java 21, Spring Boot 3.4.x, PostgreSQL 16, Flyway, React + TypeScript + Vite + Tailwind dual-app workspace, JUnit 5/Mockito/Testcontainers/Vitest/Playwright, Docker, GitHub Actions) and optional components (deferring Redis and RabbitMQ for MVP; using Spring Modulith + ArchUnit for boundary enforcement).
   - Modular Monolith architecture style: system boundaries, 4-package module layout (`api`, `web`, `application`, `internal`), SOLID & Dependency Inversion rules, DTO/Money (`BigDecimal`)/Time (UTC `Instant`) conventions (`DEC-010`), ACID transaction boundaries, and RFC 9457 Problem Details error handling.
2. **[module-map.md](module-map.md):**
   - Evaluation of all 13 candidate domain areas.
   - Detailed specification of the bounded modules (`shared`, `audit`, `branch`, `identity`, `customer`, `catalog`, `inventory`, `order`, `delivery`, `payment`, `notification`, `reporting`, and conditional `promotion`): responsibilities, owned PostgreSQL tables, public `.api` interfaces & events, allowed dependencies, forbidden dependencies, requirement mapping, and topological proof of an acyclic dependency graph (`L0` to `L7`).
3. **[cross-cutting-and-security.md](cross-cutting-and-security.md):**
   - Multi-branch data classification (central master data, single-branch operational data with `branch_id NOT NULL`, dual-branch stock transfers, customer-scoped data).
   - Shared central catalog vs. branch-isolated pricing (`REQ-004`) and branch inventory (`REQ-002`, `REQ-016`) with order line snapshots (`REQ-006`).
   - Server-side `BranchAuthorizationGuard` enforcement (`REQ-011`, `REQ-012`).
   - Security architecture: authentication & revocable token/session strategy, `DelegatingPasswordEncoder` (`bcrypt`), payment webhook HMAC verification (`REQ-009`) & idempotent deduplication (`REQ-010`), deterministic lock ordering for non-negative inventory concurrency (`REQ-007`), and the 6-category immutable audit trail (`REQ-008`, `CLAUDE.md` §8).
4. **[diagrams.md](diagrams.md):**
   - All 9 required Mermaid architecture diagrams:
     1. System Context Diagram (C4 Level 1)
     2. Container Diagram (C4 Level 2)
     3. Backend Component Diagram (C4 Level 3)
     4. Module Dependency Diagram (Topological DAG)
     5. Deployment & CI/CD Diagram (`DEC-015`)
     6. Runtime Flow 1: Authentication & Branch-Scoped Authorization (`REQ-011`, `REQ-012`)
     7. Runtime Flow 2: Online Order Placement, Nearest-Branch Allocation & Snapshots (`REQ-005`..`008`, `REQ-014`)
     8. Runtime Flow 3: Payment Webhook Verification & Idempotency (`REQ-009`, `REQ-010`)
     9. Runtime Flow 4: Inventory Locking & Inter-Branch Stock Transfer (`REQ-007`, `REQ-008`, `REQ-015`)
5. **[architecture-decisions.md](architecture-decisions.md):**
   - Phase 4 Architecture Decision Records (`ADR-0002` to `ADR-0010`, registered as `DEC-017` to `DEC-025` in [../../project-management/DECISIONS.md](../../project-management/DECISIONS.md)), building on [../adr/0001-technology-stack-and-repository-layout.md](../adr/0001-technology-stack-and-repository-layout.md).
