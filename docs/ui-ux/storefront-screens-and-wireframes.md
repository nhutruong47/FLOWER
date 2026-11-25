# Customer E-Commerce Storefront — Page Specifications & Wireframes (`frontend/storefront`)

- **Status:** PROPOSED (awaiting owner approval at Phase 6 gate before P08)
- **Last updated:** 2026-11-24
- **Owner:** frontend-developer (`hallmark` & `design-taste-frontend` skills) · **Task:** TASK-013 (Phase 6 — P07 UI/UX Design)
- **Sources:** P04 `functional-requirements.md` (`FR-002`, `FR-009`, `FR-010`, `FR-013`, `FR-014`, `FR-021`, `FR-024`, `FR-025`, `FR-030`..`033`), P06 `docs/api/openapi.yaml`, `design-system-and-components.md`.

---

## 1. Storefront Global Layout & Hallmark/Taste-Skill Rules

- **Theme Material:** **Garden (`garden`)** — Warm oat paper (`--color-paper`), deep forest ink (`--color-ink`), `Vollkorn 600` roman headings, `Hanken Grotesk` body and tabular numerals, `1px` `--color-rule` hairlines, true-colour daylight floral photography (`4px` radius, `1px` hairline outline, **zero captions beneath photos** — credits live in one expandable footer line).
- **Container & Edges:** Centered `--container-max: 1360px` with equal left and right padding (`48px` desktop, `24px` tablet, `16px` mobile) and `overflow-x: clip` on `html` and `body`.
- **Sticky Header & Branch Price Bar (`CMP-04`):**
  - Left: `FlowerChain` wordmark in `Vollkorn 600` + main links (`Seasonal arrangements`, `Bouquets`, `Branches`, `Care & support`).
  - Center/Right: **Active Branch Price Pill** (`Viewing prices & stock at: District 1 Flagship ▾`) + Wishlist count + Account link + Cart drawer trigger pill (`Bag (2)`).

---

## 2. Page-by-Page Specifications & Wireframes (15 Customer Capabilities)

### 2.1 Homepage (`/`) & Branch Selection (`CMP-04`)
- **Job & Hallmark Anatomy:** Drive the customer to browse seasonal bouquets priced and stocked for their chosen branch (`REQ-004`, `REQ-017`) or start a delivery/pickup order (`REQ-013`).
- **Hero Rule (Hallmark Floor #12 & #13):**
  - **Headline (`<= 7 words`):** `Seasonal flowers cut fresh across our branches.`
  - **Sentence (`<= 12 words`):** `Same-day home delivery from the nearest stocked shop, or in-store collection.`
  - **Actions:** Primary leaf-green pill `Shop seasonal bouquets` + secondary hairline pill `Choose your branch`.
  - **Signature in Hero:** Interactive **Branch Price & Availability Switcher** showing 3 featured branches with live distance/hours and instant price toggle.
- **Screen-by-Screen Sections (Taste-Skill `one section per screen` rhythm):**
  1. **Screen 1 — Stage Hero (`1280x800` / `1440x900`):** Columns 1–5 hold the headline, sentence, primary actions, and branch selector pill; Columns 6–12 hold a full-height daylight seasonal bouquet photograph (`4:5`).
  2. **Screen 2 — Today's Cut at Your Selected Branch (`/storefront/branches/{branchId}/products`):** 4-column hairline grid of seasonal bouquets showing the selected branch's exact `VND` price (`tabular-nums`) and live `availableStock` count (`REQ-002`, `REQ-004`, `REQ-017`).
  3. **Screen 3 — How Fulfilment Works (`--tint-moss` band):** Two-column editorial split comparing **Home Delivery** (system automatically routes your order to the nearest branch with full stock, `REQ-014`) vs. **In-Store Pickup** (collect directly at your chosen branch within 2 hours, `REQ-013`).
  4. **Screen 4 — Our Flower Shops (`/storefront/branches`):** Ruled index list of active branches (`5–30`) with street & district, operating hours (`07:00–21:00`), and `Shop this branch's prices` action.
  5. **Screen 5 — Closing Mast Footer:** Wordmark, branch directory links, care guide, order lookup link, and the single expandable Wikimedia Commons photograph credit line.

#### Wireframe — Storefront Homepage (`/`)
```text
+--------------------------------------------------------------------------------------------------+
| FlowerChain     Seasonal   Bouquets   Branches   Support   [Branch: District 1 ▾]  Wishlist Bag(2)|
+--------------------------------------------------------------------------------------------------+
|                                                                                                  |
|  Seasonal flowers cut fresh                   +-----------------------------------------------+  |
|  across our branches.                         |                                               |  |
|                                               |                                               |  |
|  Same-day home delivery from the nearest      |      [ Daylight Seasonal Bouquet Photo ]      |  |
|  stocked shop, or in-store collection.        |      True colour · 4px radius · 1px rule      |  |
|                                               |      (No caption under photograph)            |  |
|  ( Shop seasonal bouquets )  ( Choose branch )|                                               |  |
|                                               |                                               |  |
|  +-----------------------------------------+  |                                               |  |
|  | Viewing branch prices: District 1       |  |                                               |  |
|  | Open 07:00–21:00 · 18 bouquets in stock |  |                                               |  |
|  +-----------------------------------------+  +-----------------------------------------------+  |
+--------------------------------------------------------------------------------------------------+
| Today's arrangements at District 1 Branch                            Filter by category · Search |
| ------------------------------------------------------------------------------------------------ |
| +---------------------+  +---------------------+  +---------------------+  +-------------------+ |
| | [Photo: Red Rose]   |  | [Photo: White Lily] |  | [Photo: Hydrangea]  |  | [Photo: Orchid]   | |
| | Red Rose Bouquet    |  | Morning Lily Vase   |  | Cloud Hydrangea     |  | Phalaenopsis Pot  | |
| | SKU: RRB-01         |  | SKU: MLV-02         |  | SKU: CHB-04         |  | SKU: POP-09       | |
| | 450,000 VND         |  | 620,000 VND         |  | 540,000 VND         |  | 1,250,000 VND     | |
| | 12 available here   |  | 5 available here    |  | 3 available here    |  | Out of stock here | |
| | ( Add to bag )      |  | ( Add to bag )      |  | ( Add to bag )      |  | ( Check branches )| |
| +---------------------+  +---------------------+  +---------------------+  +-------------------+ |
+--------------------------------------------------------------------------------------------------+
```

---

### 2.2 Product Listing, Category Filter & Product Search (`/catalog`)
- **API Binding:** `GET /api/v1/storefront/categories` and `GET /api/v1/storefront/branches/{branchId}/products?categoryId=...&q=...&page=0&size=20`.
- **Key Features:**
  - **Branch Context Banner:** Clearly states `Prices and stock shown for: [Selected Branch Name] (Switch branch)`.
  - **Instant Search & Category Strip:** Debounced search input (`q`, with clear button and `<label>Search bouquets by flower or SKU</label>`) + horizontal category filter pills (`All arrangements`, `Hand-tied bouquets`, `Vase arrangements`, `Sympathy`, `Seasonal`).
  - **Availability Filter:** Toggle `Show only in-stock at this branch` (`availableStock > 0`).
  - **Pagination:** Server-paginated (`size = 20`, max `100`, `NFR-001`) with total count (`Showing 1–20 of 48 arrangements`).

---

### 2.3 Product Detail Page (`/products/:slug`) & Branch Availability Matrix
- **API Binding:** Product details + current branch price/stock (`GET /api/v1/storefront/branches/{branchId}/products`) + cross-branch stock check across active branches (`GET /api/v1/storefront/branches`).
- **Key Features:**
  - **Left Column (Cols 1–7):** Large daylight product photograph (`4:5` aspect ratio, `4px` radius, `1px` hairline border).
  - **Right Column (Cols 8–12):**
    - Title in `Vollkorn 600`, SKU code, and unit label (`BOUQUET` / `VASE`).
    - **Branch Price & Stock Box:** Shows the selected branch's `unitPrice` (`450,000 VND`) and `availableStock` (`12 available at District 1 Branch`).
    - **Quantity Stepper:** Clamped strictly between `1` and `availableStock` so the user cannot add more units than available (`REQ-007`).
    - **Actions:** Primary pill `Add to bag` + secondary pill `Save to wishlist`.
    - **Check Price & Stock at Other Branches Drawer:** Expandable hairline table listing all active branches, each branch's price for this SKU (`REQ-004`), and whether it is in stock for pickup (`REQ-002`, `REQ-013`).

---

### 2.4 Shopping Cart Drawer & `/cart` Page
- **Key Features:**
  - Lists each cart item with thumbnail, product name, SKU, unit price at the current browsing branch, quantity stepper (`1..availableStock`), and line subtotal (`tabular-nums`).
  - **Pre-Checkout Fulfilment Preview:** Radio toggle (`Home Delivery` vs. `In-Store Pickup`) right in the cart drawer with a note:
    - *"For home delivery, the system automatically assigns the nearest branch with full stock at checkout. If that branch has a different price, you will review and confirm the updated total before paying."* (`REQ-014`, `REQ-017`, `B11`).
  - Primary pill `Proceed to checkout`.

---

### 2.5 Checkout Page (`/checkout`) — Branch Selection, Delivery/Pickup, Price Reconciliation & Payment
- **API Binding:**
  1. `POST /api/v1/storefront/orders/quote` (calculates `processingBranchId`, `priceChangedFromBrowsingBranch`, line snapshots, `deliveryFee`, and `totalAmount`).
  2. `POST /api/v1/storefront/orders` with `Idempotency-Key` header (`UUIDv4` generated when checkout step opens and rotated only on payload change).
- **3-Step Single-Screen Hairline Layout:**
  - **Step 1 — Fulfilment Method (`CMP-06`, `REQ-013`):**
    - **Tab A: Home Delivery (`DELIVERY`, `REQ-014`):** Customer selects a saved address (`FR-031`) or enters `recipientName`, `recipientPhone`, `deliveryStreetAddress`, `deliveryDistrict`, `deliveryCity` (plus coordinates/district zone) and preferred delivery time slot (`requestedSlotStart` / `requestedSlotEnd`). **No manual branch picker is shown** (`B3b`, `REQ-012`, `REQ-014`).
    - **Tab B: In-Store Pickup (`PICKUP`, `B3c`):** Customer selects an active branch from a radio list showing only branches that currently hold sufficient stock for every item in the cart, plus pickup time slot.
  - **Step 2 — Server Quote & Branch Price Reconciliation (`CMP-07`, `FR-014`, `B11`):**
    - Triggered automatically when Step 1 is completed (`POST /api/v1/storefront/orders/quote`).
    - Displays: **`Assigned Processing Branch: [Branch Name & Address]`** (`REQ-005`).
    - **If `priceChangedFromBrowsingBranch == true`:** Renders a prominent `--tint-clay` hairline notice comparing the browsing branch price vs. the assigned processing branch price and requires checking:
      `[x] I confirm the updated total of 950,000 VND from [Assigned Branch Name]`.
  - **Step 3 — Payment Method & Order Placement (`FR-021`, `FR-025`):**
    - Payment options (`B4`): `Online Payment Gateway (Signed Webhook Verification)` or `Cash on Delivery / Pay at Store Pickup`.
    - Optional Voucher Code input (`FR-032`, conditional on `B6`).
    - Primary CTA pill: `Place order · 950,000 VND` (sends `confirmedTotalAmount` and `Idempotency-Key`).

#### Wireframe — Checkout with Auto-Allocated Branch & Price Reconciliation (`/checkout`)
```text
+--------------------------------------------------------------------------------------------------+
| FlowerChain Checkout                                                      Back to shopping bag   |
+--------------------------------------------------------------------------------------------------+
| 1. Fulfilment method (REQ-013)                    | Order summary & immutable snapshot preview   |
| (•) Home delivery     ( ) In-store pickup         | -------------------------------------------- |
|                                                   | Assigned processing branch (REQ-005/014):    |
| Recipient name *        Recipient phone *         | Binh Thanh Branch (2.1 km from address)      |
| [ Nguyen Thi Lan      ] [ 0909123456            ] | All items verified in stock at this branch.  |
|                                                   |                                              |
| Street address *                                  | +------------------------------------------+ |
| [ 123 Dien Bien Phu                             ] | | Notice: Branch price updated (REQ-017)   | |
| District *              City *                    | | You browsed District 1 prices (450,000). | |
| [ Binh Thanh ▾        ] [ Ho Chi Minh City      ] | | Nearest branch with full stock is Binh   | |
|                                                   | | Thanh Branch (460,000 VND / bouquet).    | |
| Preferred delivery window                         | | [x] I confirm the updated branch price   | |
| [ Today, 14:00 – 16:00 ▾                        ] | +------------------------------------------+ |
| ------------------------------------------------- |                                              |
| 2. Payment method                                 | Red Rose Bouquet (RRB-01) x 2    920,000 VND |
| (•) Online payment gateway (Instant verification) | Delivery fee (2.1 km)             30,000 VND |
| ( ) Pay on delivery (COD)                         | -------------------------------------------- |
|                                                   | Total to pay                     950,000 VND |
|                                                   |                                              |
|                                                   | (      Place order · 950,000 VND      )      |
+--------------------------------------------------------------------------------------------------+
```

---

### 2.6 Online Payment Verification & Return Screen (`/checkout/payment-status/:orderId`)
- **Security Invariant (`REQ-009`, `CLAUDE.md` §8):** Returning to the browser redirect URL **never** marks the order paid.
- **Behavior:**
  - Displays a calm status card: `Verifying payment confirmation from payment provider…`
  - Polls `GET /api/v1/storefront/orders/{orderId}` every 2 seconds until the server-side HMAC-verified webhook (`POST /api/v1/webhooks/payments/{provider}`) transitions `paymentStatus` from `UNPAID` to `PAID` and `status` to `CONFIRMED`.
  - If payment fails or expires, displays a retry payment action or cancellation option.

---

### 2.7 Order Tracking, Customer Account, Order History, Wishlist, Promotions & Support
1. **Order Tracking & Receipt Detail (`/account/orders/:orderId` — `FR-002`, `FR-024`, `FR-030`, `FR-031`):**
   - Displays `OrderStatusTimeline` (`CMP-09`), the assigned **`processingBranchName`** and branch phone/address (`REQ-005`), and the **`OrderSnapshotReceiptTable`** (`CMP-08`) rendered strictly from `skuSnapshot`, `productNameSnapshot`, `unitPriceSnapshot`, and `lineTotalSnapshot` (`REQ-006`).
   - Shows `Cancel order` button only while `status` is `PENDING_PAYMENT` or `CONFIRMED` (before `PREPARING`, `FR-024`), and `Report delivery/flower issue` button once `COMPLETED` (`FR-030`).
2. **Customer Account & Saved Addresses (`/account` — `FR-002`, `FR-031`):**
   - Profile details (`fullName`, `email`, `phone`) and saved delivery address cards (`label`, `recipientName`, `streetAddress`, `district`, `city`, `Default` badge).
3. **Order History (`/account/orders` — `BRULE-027`):**
   - Paginated hairline table of the authenticated customer's own orders (`Order #`, `Date`, `Fulfilment`, `Processing Branch`, `Status`, `Total`, `View details`). Attempting to open another customer's order ID renders the standard `404 Resource Not Found` state (`CLAUDE.md` §7).
4. **Wishlist (`/wishlist`):**
   - Grid of saved products showing the current browsing branch's live price and stock availability, with one-click `Move to bag`.
5. **Promotions (`/promotions` — Conditional `B6` / `FR-032`):**
   - Editorial seasonal floral campaign page listing active voucher codes (if enabled under `B6`) with validity dates, minimum order totals, and `Copy voucher code` action.
6. **Customer Support (`/support` — `FR-030`, `FR-033`):**
   - Floral care instructions, delivery radius & pickup policy FAQ, branch contact list, and a shortcut to select a completed order to submit a post-delivery return/refund issue (`FR-030`).
