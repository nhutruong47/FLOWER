# Design System, Brand Direction, and Component Inventory (Hallmark & Taste-Skill Governed)

- **Status:** PROPOSED (awaiting owner approval at Phase 6 gate before P08)
- **Last updated:** 2026-11-24
- **Owner:** frontend-developer (`hallmark`, `design-taste-frontend`, and `frontend-development` skills) · **Task:** TASK-013 (Phase 6 — P07 UI/UX Design)
- **Sources:**
  - Hallmark v2.0.0 Deal (`flowerchain-multi-branch-botanical-flower-5764·0b9c61ef·2026-11-24·r0` → Theme **Garden (`garden`)**)
  - Taste-Skill v2.5.1 Deal (`--page place --trade florist` → Palette **`green-mat-chocolate`** / **Garden Oat & Chlorophyll**, Pairing **`Vollkorn` + `Hanken Grotesk` + `Newsreader`**)
  - P04 Functional & Non-Functional Requirements (`FR-001`..`038`, `NFR-002`, `NFR-008`, `NFR-013`)
  - P06 OpenAPI 3.1 Contract (`docs/api/openapi.yaml`)

---

## 1. Hallmark & Taste-Skill Deals and Anti-Slop Design Floor

### 1.1 The Hallmark Picks Block

```markdown
**Hallmark · v2.0.0 & Taste-Skill · v2.5.1**

- **Classification** · Storefront: `place` / `product` (site-kind `other`, floral retail chain) · Admin: `app` (Workbench scope, internal multi-branch operations)
- **Theme** · **Garden (`garden`)** dealt 2/3 (warm oat paper `oklch(95.8% 0.024 90)`, deep forest-green ink `oklch(23% 0.050 152)`, Vollkorn 600 roman over Hanken Grotesk 400/500, 1px hairlines, soft moss and clay tints, daylight floral photography in true colour, one chlorophyll leaf-green signal `oklch(46% 0.135 142)`)
- **Chrome** · Storefront nav: edge-aligned wordmark, active branch price selector pill (`REQ-004`, `REQ-017`), catalog links, and cart drawer trigger · Admin nav: fixed left workbench rail with server-resolved branch scope badge (`REQ-011`, `REQ-012`)
- **Hero (Storefront)** · Stage hero: 7-word Vollkorn 600 headline + 12-word sentence + primary pill CTA + active branch selector on columns 1–5; true-colour daylight seasonal bouquet photograph on columns 6–12 (visible at `1280x800` without scrolling)
- **Signature** · Live **Branch Price & Fulfilment Bar** in the storefront hero and sticky header: switching between branches immediately updates per-branch prices (`REQ-004`, `REQ-017`) and shows real-time branch stock (`REQ-002`, `REQ-007`)
- **Anti-Slop Floor Rules Enforced** ·
  1. Zero literal colours or `font-family` outside `:root` tokens.
  2. Never `transition: all`; never animate `width`, `height`, `top`, or `left` (`transform` and `opacity` only).
  3. Never one italic word or coloured word inside a roman heading.
  4. `overflow-x: clip` on `html` and `body` (never `100vw`).
  5. Never eyebrows or tracked all-caps / small-caps labels: all labels, table heads, badges, and nav links are `Hanken Grotesk 500`, sentence case, untracked.
  6. Never purple/violet/indigo gradients, cards inside cards (max nesting depth: 2), emoji icons, decorative status dots, or generic SaaS admin templates.
  7. Zero WebGL / shader effects on the floral storefront (`site-kind: other` uses daylight photography, flat warm paper, hairlines, and quiet hover micro-interactions).
```

---

## 2. Design Tokens (`frontend/packages/shared/src/styles/tokens.css`)

All colours, typography, spacing, borders, and motion durations in both `frontend/storefront` and `frontend/admin` map exclusively to these CSS custom properties and Tailwind utility aliases:

```css
/* hallmark: v2.0.0 · theme: garden · deal: 2/3 flowerchain-multi-branch-botanical-flower-5764·0b9c61ef·2026-11-24·r0 · genre: editorial */
:root {
  /* Paper & Surface Hierarchy (Warm Oat & Botanical Neutrals — never cold grey or slate) */
  --color-paper: oklch(95.8% 0.024 90);       /* Primary page background (#F6F2E9) */
  --color-paper-2: oklch(92.6% 0.030 90);     /* Secondary well / table header / hover row (#ECE6D8) */
  --color-paper-3: oklch(89.0% 0.034 90);     /* Recessed well / active surface (#E1D9C7) */
  --color-surface-elevated: oklch(98.2% 0.012 90); /* Clean sheet for admin data tables & modals (#FCFBF7) */

  /* Ink Hierarchy (Deep Botanical Forest Ink) */
  --color-ink: oklch(23.0% 0.050 152);        /* Primary headings & strong figures (#14281D) */
  --color-ink-2: oklch(33.0% 0.048 150);      /* Running body copy (#294233) */
  --color-muted: oklch(46.0% 0.034 142);      /* Secondary labels, table heads, timestamps (#53685A) */

  /* Hairlines & Structural Rules (1px flat editorial architecture — no drop shadows) */
  --color-rule: oklch(83.0% 0.024 112);       /* 1px row & section divider (#CECBB8) */
  --color-rule-2: oklch(68.0% 0.042 138);     /* 1px input border, secondary button & table top rule (#92A392) */

  /* Botanical Chlorophyll Signal & Tints (Accent kept < 5% of screen area) */
  --color-accent: oklch(46.0% 0.135 142);     /* Primary CTA pill, focus ring, active link underline (#2D6E3E) */
  --color-accent-hover: oklch(40.0% 0.130 142); /* Primary CTA hover step (#235B31) */
  --color-accent-2: oklch(86.0% 0.075 128);   /* Pale leaf highlight on dark green ink bands (#C3DFB8) */
  --color-accent-ink: oklch(97.0% 0.015 95);  /* Text on primary accent pill (#FAF8F2) */
  --color-focus: oklch(46.0% 0.135 142);      /* Instant :focus-visible outline */
  --tint-moss: oklch(46.0% 0.135 142 / 0.11); /* Soft moss band & active branch badge background */
  --tint-clay: oklch(54.0% 0.140 46 / 0.10);  /* Warm clay band for seasonal notes & warnings */

  /* Semantic Operational States (No neon; natural botanical pigments) */
  --color-success-ink: oklch(35.0% 0.110 145);
  --color-success-bg: oklch(46.0% 0.135 142 / 0.14);
  --color-warning-ink: oklch(42.0% 0.120 65);
  --color-warning-bg: oklch(68.0% 0.140 70 / 0.16);
  --color-danger-ink: oklch(40.0% 0.155 25);  /* Oxblood / crimson for RFC 9457 errors, spoilage & negative alerts */
  --color-danger-bg: oklch(52.0% 0.165 25 / 0.12);

  /* Typography */
  --font-display: "Vollkorn", ui-serif, Georgia, serif;
  --font-body: "Hanken Grotesk", ui-sans-serif, system-ui, sans-serif;
  --font-outlier: "Newsreader", ui-serif, Georgia, serif;
  --display-weight: 600;
  --tracking-display: -0.012em;

  /* Type Scale */
  --text-xs: 0.8125rem;      /* 13px — auxiliary metadata */
  --text-sm: 0.875rem;       /* 14px — labels, table heads, badges (sentence case, untracked) */
  --text-base: 1rem;         /* 16px — minimum body copy & form inputs */
  --text-lg: 1.125rem;       /* 18px — lead paragraph & item titles */
  --text-xl: 1.375rem;       /* 22px — card/panel headings */
  --text-2xl: 1.75rem;       /* 28px — sub-section headings */
  --text-3xl: 2.375rem;      /* 38px — section headings (h2) */
  --text-display: clamp(2.5rem, 4.2vw, 3.5rem); /* 40px–56px — hero headline (h1) */

  /* Spacing, Layout & Geometry */
  --space-2xs: 0.25rem;      /* 4px */
  --space-xs: 0.5rem;        /* 8px */
  --space-sm: 0.75rem;       /* 12px */
  --space-md: 1rem;          /* 16px */
  --space-lg: 1.5rem;        /* 24px */
  --space-xl: 2rem;          /* 32px */
  --space-2xl: 3rem;         /* 48px */
  --space-3xl: 4.5rem;       /* 72px */
  --container-max: 1360px;   /* Capped & centred container; equal left/right edges */
  --grid-gap: 1.5rem;        /* 24px single column gap invariant below hero */
  --radius-card: 4px;        /* Crisp 4px corners on photos, wells, inputs & admin panels */
  --radius-pill: 999px;      /* Pill buttons & branch/status tags */
  --rule-hair: 1px;          /* 1px structural hairline */
  --shadow-card: none;       /* Flat paper depth — zero drop shadows */

  /* Motion (State only; prefers-reduced-motion respected) */
  --dur-short: 160ms;
  --dur-med: 260ms;
  --ease-out: cubic-bezier(0.22, 1, 0.36, 1);
}
```

### 2.1 Typography Rules
1. **Headings (`h1`, `h2`, `h3`):** `Vollkorn 600`, roman, sentence case, `line-height: 1.04`, `letter-spacing: -0.012em`, colour `var(--color-ink)`. Never italic, never mixed weights inside one heading, never over 7 words in the hero `h1`.
2. **Body Copy:** `Hanken Grotesk 400` at `16px` (`1rem`), `line-height: 1.55`, colour `var(--color-ink-2)`.
3. **Prices, Stock Counts, Quantities & Financial Tables:** `Hanken Grotesk 500` with **`font-variant-numeric: lining-nums tabular-nums`** so `VND` prices (`450.000 ₫` / `500,000.00 VND`), stock deltas (`+25`, `-4`), and revenue columns align vertically to the pixel.
4. **Labels, Table Headers, Badges, Form Labels & Navigation:** `Hanken Grotesk 500` at `14px` (`0.875rem`), **sentence case, `letter-spacing: 0` (never `uppercase` + `tracking-widest`)**, colour `var(--color-muted)`.

---

## 3. Shared Component Inventory (`frontend/packages/shared` + App Components)

Every interactive control implements all **8 interaction states** (`default`, `hover`, `focus-visible`, `active/pressed`, `disabled`, `loading`, `error`, `readonly/completed`) and meets the `44px × 44px` minimum touch target at mobile/tablet widths.

| Component ID | Component Name | Package / App | Purpose & Governing Requirements |
|---|---|---|---|
| **CMP-01** | `Button` (`primary`, `secondary`, `danger`, `ghost`) | `packages/shared` | Single primary pill style (`--color-accent` bg, `--color-accent-ink` text, `--radius-pill`) and hairline-outlined secondary pill (`1px solid var(--color-rule-2)`). Lifts/steps on hover (`transform: translateY(-1px)`), presses on click, instant `2px` `--color-focus` ring on `:focus-visible`. |
| **CMP-02** | `FormField` (`TextInput`, `NumberInput`, `Select`, `Textarea`, `DateSlotPicker`) | `packages/shared` | `4px` radius (`--radius-card`), `1px solid var(--color-rule-2)` border on `--color-paper`, visible `<label for="...">` above input (never placeholder-only), inline field error message underneath bound via `aria-describedby` (`NFR-008`, `NFR-013`). |
| **CMP-03** | `ProblemAlertBanner` | `packages/shared` | Parses RFC 9457 `application/problem+json` responses (`title`, `detail`, `errorCode`, `requestId`, and field `errors` map) into a clear, non-technical banner with support `requestId` copy button (`CLAUDE.md` §7, `NFR-008`). |
| **CMP-04** | `BranchPriceSelectorBar` | `storefront` | Sticky header / hero bar allowing the customer to choose their browsing branch/district (`REQ-004`, `REQ-017`, `B11`). Changing the branch re-fetches `/api/v1/storefront/branches/{branchId}/products` and updates displayed prices and stock badges live. |
| **CMP-05** | `ProductBouquetCard` | `storefront` | Displays `4:5` daylight floral photo (`4px` radius, `1px` `--color-rule` hairline, subtle `1.02` scale on hover inside `overflow: hidden` frame, **no caption under image**), Vollkorn 600 title, SKU, active branch `VND` price (`tabular-nums`), and real-time branch stock badge (`In stock: 8` or `Out of stock at this branch`). |
| **CMP-06** | `FulfilmentModeSwitcher` | `storefront` | Segmented toggle at checkout between **Home Delivery** (address + coordinates → system auto-selects nearest branch with stock, `REQ-013`, `REQ-014`) and **In-Store Pickup** (customer selects active pickup branch with stock, `REQ-013`, `B3c`). |
| **CMP-07** | `CheckoutPriceReconciliationNotice` | `storefront` | Shown when `OrderQuoteResponse.priceChangedFromBrowsingBranch == true` (`FR-014`, `B11`): compares the browsing branch price vs. the auto-selected delivery processing branch price line-by-line and requires an explicit customer confirmation checkbox before `POST /api/v1/storefront/orders` is enabled. |
| **CMP-08** | `OrderSnapshotReceiptTable` | `packages/shared` | Renders immutable order lines (`productNameSnapshot`, `skuSnapshot`, `unitPriceSnapshot`, `quantity`, `lineTotalSnapshot`) and the assigned `processingBranchName` (`REQ-005`, `REQ-006`). Used in Storefront Order Tracking and Admin Order Detail. |
| **CMP-09** | `OrderStatusTimeline` | `packages/shared` | Step-by-step hairline progress ledger showing `PENDING_PAYMENT → CONFIRMED → PREPARING → READY_FOR_DELIVERY / READY_FOR_PICKUP → OUT_FOR_DELIVERY → COMPLETED` (or `CANCELLED`) with UTC/local timestamps and actor notes (`FR-022`, `BRULE-023`). |
| **CMP-10** | `AdminBranchScopeSwitcher` | `admin` | Top-bar indicator showing the staff user's server-resolved `authorizedBranchIds` (`REQ-011`, `REQ-012`). For single-branch staff, it renders as a locked badge (`Branch: District 1 Flagship`); for `SYSTEM_OWNER` / `HEAD_OFFICE_ADMIN` (or multi-branch managers), it allows filtering by `All Authorized Branches` or a specific branch. |
| **CMP-11** | `HairlineDataTable` & `PaginationBar` | `admin` | Editorial flat data table (`1px` `--color-rule-2` top border, `1px` `--color-rule` row dividers, tabular numerals, sentence-case headers) with server-driven pagination (`page`, `size <= 100`, `totalElements`, `NFR-001`). |
| **CMP-12** | `StockLevelBadge` & `MovementDeltaCell` | `admin` | Displays `availableStock` (`>= 0`, `REQ-007`) and `reservedStock`, plus signed movement deltas (`+25` in `--color-success-ink`, `-4` in `--color-danger-ink`) linked to source documents (`REQ-008`). |
| **CMP-13** | `StockTransferDualBranchCard` | `admin` | Visualizes `sourceBranch` → `targetBranch` transfer lifecycle (`REQUESTED → APPROVED → IN_TRANSIT → COMPLETED`). Enforces button availability based on whether the logged-in user is authorized for `sourceBranchId` (Dispatch `-qty`) or `targetBranchId` (Receive `+qty`) (`REQ-011`, `REQ-015`, `FR-018`). |
| **CMP-14** | `AuditDiffDrawer` | `admin` | Read-only inspection drawer for `inventory_movements` (`REQ-008`) and `audit_logs` (`FR-036`), rendering `actorId`, `actorType`, UTC `createdAt`, `reasonCode`, and side-by-side `oldValues` vs. `newValues` JSON snapshots. |
| **CMP-15** | `StateFeedbackBlock` (`Skeleton`, `EmptyState`, `ErrorState`, `ForbiddenState`) | `packages/shared` | Unified feedback patterns required on every route (`NFR-013`): warm oat pulse skeletons during loading, honest sentence-case empty states with one clear action, and RFC 9457 error recovery blocks. |
