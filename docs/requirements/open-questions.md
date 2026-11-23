# Open questions

Questions that must be answered before the related features are built. No business rule is assumed until it appears in an answer here or in a requirements document.

## Business (owner: product owner / business)

| # | Question | Why it matters | Answer |
|---|---|---|---|
| B1 | Do written requirements or user stories already exist? | They would be the source for the module list and the backlog. | _open_ |
| B2 | Is stock kept per branch, in a central warehouse, or both? Can branches transfer stock to each other? | This shapes the inventory and catalog data model. | _Partly answered:_ inventory belongs to a specific branch (REQ-002, owner brief 2026-11-23). Central warehouse and branch transfers: _open_ |
| B3 | How are online orders fulfilled (store pickup, delivery or both), and how is the fulfilling branch chosen? | This shapes ordering, branch selection and delivery. | _Partly answered:_ every order has a traceable processing branch (REQ-005). Fulfilment method and branch selection: _open_ |
| B4 | Which payment methods and payment providers must be supported? | Needed for checkout and provider integration. | _open_ (webhooks must be verified on the server and processed once: REQ-009, REQ-010) |
| B5 | Which staff roles exist (head office, branch manager, branch staff, ...) and what can each one see and do? | Needed for security and the admin app. | _open_ (the backend enforces branch-level authorization: REQ-011, REQ-012) |
| B6 | Do customers need accounts? Are loyalty points, vouchers or promotions in scope? | Needed for customer and promotion modules. | _open_ |
| B7 | Which language(s) and currency? Are there tax or e-invoice rules? | Needed for i18n, pricing and invoicing. | _open_ |
| B8 | Are there brand guidelines, a logo or design mockups? | Needed for the storefront UI. | _open_ |
| B9 | Do product prices differ between branches? If so, who sets them, and does the storefront show the price of the customer's chosen branch? | REQ-004 is NEEDS_CONFIRMATION. This decides the pricing model. | _open_ |
| B10 | Can a branch sell products that are not in the central catalog, or hide central products? | REQ-003 says the catalog is central "unless approved otherwise". | _open_ |

## Technical

| # | Question | Answer |
|---|---|---|
| T1 | Backend build tool | **Maven (wrapper)**, decided 2026-11-23 |
| T2 | Frontend structure | **Two apps (storefront, admin) plus a shared package**, decided 2026-11-23 |
| T3 | Node package manager | **npm (workspaces)**, decided 2026-11-23 |
| T4 | Local Docker: Docker Desktop (needs WSL2, admin rights, and a paid licence at larger companies), an alternative, or CI only? | **No local Docker.** Integration tests run in CI only. Owner, 2026-11-23 (DEC-015) |
| T5 | Login method: server session or JWT? Social login for customers? | _open_ |
| T6 | Hosting and deployment target (cloud provider, VPS, on-premises)? | _open_ |
| T7 | Is the GitHub repository public or private? Keep the name "FLOWER" or rename it to "FlowerChain"? | _open_ |
| T8 | Branching workflow: feature branches + pull requests into `main`, with branch protection? | _open_ |
| T9 | Enforce backend module boundaries with Spring Modulith, ArchUnit, or by convention only? | _open_ |
| T10 | PostgreSQL major version to target? | _open_ |
| T11 | Which frontend unit and component test tool? (The brief lists only Playwright, which is for end-to-end tests.) | _open_ (decide in Phase 4) |
| T12 | Without local Docker (DEC-015), how do developers run PostgreSQL to try the app by hand: portable PostgreSQL binaries (no admin), a shared development database, or automated tests only? | _open_ (decide before Phase 7) |
