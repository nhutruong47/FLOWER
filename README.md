# FLOWER

**FlowerChain**: a management and e-commerce platform for a business that runs multiple flower shop branches (initial target: 5–30 branches). It covers the customer-facing online shop and the internal system used by head office and branch staff.

> **Status:** Phase 1, project governance (awaiting owner approval). There is no application code yet. Live status: [project-management/CURRENT_STATE.md](project-management/CURRENT_STATE.md).

## Planned stack

| Area | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3.x (Spring MVC, Spring Security, Spring Data JPA), Maven wrapper |
| Database | PostgreSQL, Flyway migrations |
| Frontend | React, TypeScript, Vite, Tailwind CSS: two apps (storefront, admin) in an npm workspace |
| Architecture | Modular monolith |
| Testing | JUnit 5, Mockito, Testcontainers, Playwright |
| Infrastructure | Docker, GitHub Actions |

The reasons for these choices are in [docs/adr/0001-technology-stack-and-repository-layout.md](docs/adr/0001-technology-stack-and-repository-layout.md).

## Repository layout (planned)

```
backend/              Spring Boot modular monolith
frontend/
  storefront/         Public customer shop
  admin/              Internal management for head office and branches
  packages/shared/    Shared types, API client and UI pieces
infra/                Dockerfiles (images are built in CI)
docs/                 Architecture decisions, requirements, open questions
.github/workflows/    CI pipelines
```

Each folder is added in the phase that first needs it.

## Prerequisites

- Git
- JDK 21 (Eclipse Temurin)
- Node.js LTS, which includes npm
- Docker is **not** needed locally (DEC-015). Integration tests with Testcontainers run in GitHub Actions.

You don't need to install Maven: the backend will include the Maven wrapper (`mvnw`).

## Conventions

- Never commit secrets. Keep local settings in `.env` files, which Git ignores, and commit only `.env.example`.
- Line endings are LF everywhere except `.cmd`/`.bat` files. `.gitattributes` enforces this.
- Formatting basics are set in `.editorconfig`.

## Project governance

- [CLAUDE.md](CLAUDE.md): rules for everyone working in the repo (people and Claude Code): business guardrails, conventions, security, testing, Git workflow.
- [project-management/](project-management/): roadmap, tasks, progress, decisions, blockers, bugs and current state.
- [.claude/](.claude/): Claude Code specialist agents, skills, safe permission settings and verified hooks ([hooks README](.claude/hooks/README.md)).

## Documentation

- [docs/requirements/](docs/requirements/): requirements register (REQ) and open questions
- [docs/business-analysis/](docs/business-analysis/), [docs/architecture/](docs/architecture/), [docs/adr/](docs/adr/), [docs/database/](docs/database/), [docs/api/](docs/api/), [docs/ui-ux/](docs/ui-ux/), [docs/testing/](docs/testing/), [docs/deployment/](docs/deployment/): one folder per discipline. Each README says what belongs there.
