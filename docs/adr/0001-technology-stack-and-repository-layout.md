# ADR 0001: Technology stack and repository layout

- **Status:** Accepted
- **Date:** 2026-11-23

## Context

FlowerChain is a new platform for one business that runs multiple flower shop branches (initial target: 5–30). It needs a customer-facing online shop and an internal management system.

The Phase 0 inspection found an empty workspace: only `README.md` and a Git repository connected to `github.com/nhutruong47/FLOWER`. No existing code or configuration had to be kept compatible.

## Decisions

1. **Architecture: modular monolith.** One deployable Spring Boot application, split internally into business modules with clear boundaries. The module list will come from the confirmed requirements, not from guesses.
2. **Backend: Java 21 and Spring Boot 3.x** (Spring MVC, Spring Security, Spring Data JPA), built with **Maven through the Maven wrapper**, so contributors don't need a global Maven install.
3. **Database: PostgreSQL**, with the schema managed only by **Flyway** migrations.
4. **Frontend: two React + TypeScript + Vite + Tailwind CSS apps** in an **npm workspace** under `frontend/`:
   - `storefront/`: the public customer shop.
   - `admin/`: internal management for head office and branch staff.
   - `packages/shared/`: code both apps use (types, API client, UI pieces).

   We use two apps so that internal admin code is never shipped to public customers, and so each app can be built and deployed on its own.
5. **Testing:** JUnit 5 and Mockito for unit tests, Testcontainers (real PostgreSQL) for integration tests, Playwright for end-to-end tests.
6. **Infrastructure:** Docker images and GitHub Actions CI.
7. **Repository:** a single monorepo with `backend/`, `frontend/`, `infra/`, `docs/` and `.github/workflows/` at the root.

## Consequences

- Code shared between the storefront and admin apps must live in `frontend/packages/shared`. The two apps must not import from each other.
- Docker is not installed on the development machine yet. Until it is, Testcontainers tests and the local PostgreSQL container can only run in GitHub Actions (its Linux runners include Docker).
- All files use LF line endings (`.gitattributes`), so the `mvnw` script and shell scripts work in Linux containers and CI.
- How module boundaries are enforced in the backend (for example with Spring Modulith or ArchUnit tests) is still open. See [open questions](../requirements/open-questions.md).
