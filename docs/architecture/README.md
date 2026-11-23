# Architecture

- **Owner:** solution-architect · **Phase:** 4 · **Status:** empty. The baseline stack is in [ADR-0001](../adr/0001-technology-stack-and-repository-layout.md).

This folder will hold:

- `module-map.md`: backend modules, their responsibilities and public APIs, which REQs each one covers, and which modules may depend on which.
- `context-and-containers.md`: system context and container diagrams (storefront, admin, backend, database, payment provider).
- `cross-cutting.md`: authentication, branch scoping, auditing, error handling, observability.

Major decisions are recorded as ADRs in [../adr/](../adr/) and registered in [DECISIONS.md](../../project-management/DECISIONS.md).
