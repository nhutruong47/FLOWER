---
name: deployment
description: Repeatable workflow for FlowerChain build/deploy infrastructure - Dockerfiles, local docker-compose, GitHub Actions CI/CD, environment variable templates and runbooks - with explicit owner approval before any deployment or production-related action. Use in Phases 11-12 or when changing infra/ or .github/workflows/.
---

# Deployment

## Required input
- The build and test commands (`CLAUDE.md` §3) and the services to package (backend, storefront, admin)
- The hosting target (T6) and the local Docker decision (T4); if either is undecided, stop at the steps that need it
- The list of required settings (names only)

## Workflow
1. **List the environments** (local, CI, staging, production) in `docs/deployment/environments.md`: what runs where, which settings each needs (names, never values), and who may access it.
2. **Containerize.** Use multi-stage Dockerfiles for the backend and each frontend. Run as a non-root user, pin base image versions, and keep secrets out of the image.
3. **Local stack.** Add `infra/docker-compose.yml` for PostgreSQL (once T4 is decided), with `.env.example` placeholders.
4. **CI.** `.github/workflows/` builds and runs all tests (Testcontainers on Linux runners) on every pull request, fails on any test failure, and checks dependencies. Reference secrets by name only (`${{ secrets.NAME }}`).
5. **Prove it on a feature branch.** Run the pipeline and record the link and result. Show once that a failing test fails the pipeline.
6. **Write runbooks** for deploy, roll back, back up and restore, and rotate secrets, in `docs/deployment/runbooks/`.
7. **Deploy only with approval.** Before **every** deployment or production-related action, show the owner the exact action and its impact, and wait for explicit approval.

## Output format

```markdown
## Infra change: TASK-xxx (<date>)
Files: <list>
Pipeline run: <link or local log> → <result>
New settings required: <NAME — purpose — where it is set> (no values)
Approval needed for: <none | exact deploy/production action>
```

## Quality checks
- No secret values in the repository, images, logs or workflow files. `.env.example` lists every setting.
- CI gates aren't weakened (no `continue-on-error` on tests, no skipped tests) without owner approval.
- Builds are reproducible: pinned versions and committed lockfiles.
- Every automated deployment step has a runbook, including how to roll back.

## Stop conditions
- Any deployment, production access, creation of cloud resources or secrets, or DNS change without explicit owner approval for that specific action.
- Hosting, cost or licensing decisions are open (T4, T6).
- Required credentials are missing. Ask the owner to configure them; never create or guess them.
- CI fails for reasons you can't explain.
