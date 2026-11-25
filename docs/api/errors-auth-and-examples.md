# API Error Catalog, Authorization Matrix, Webhooks, and Examples

- **Status:** PROPOSED (awaiting owner approval at Phase 5 gate before P07)
- **Last updated:** 2026-11-24
- **Owner:** solution-architect & backend-developer · **Task:** TASK-012 (Phase 5 — P06 Database and API Design)
- **Sources:** `CLAUDE.md` §7, §8, [openapi.yaml](openapi.yaml), P04 `roles-and-permissions.md`, P05 `cross-cutting-and-security.md`.

---

## 1. API Versioning, Pagination, Sorting, Filtering, and Money Conventions (`CLAUDE.md` §5, §7)

1. **Versioning & Route Groups:**
   - Base path: `/api/v1`
   - Customer Storefront routes: `/api/v1/storefront/**`
   - Internal Admin routes: `/api/v1/admin/**`
   - Payment Provider Webhooks: `/api/v1/webhooks/payments/**`
2. **Pagination, Sorting & Filtering (`NFR-001`):**
   - Query parameters: `?page=0&size=20&sort=createdAt,desc`
   - Default `size = 20`, maximum `size = 100` (requests with `size > 100` return `400 Bad Request` Problem Details).
   - Standard paginated envelope:
     ```json
     {
       "items": [],
       "page": 0,
       "size": 20,
       "totalElements": 42,
       "totalPages": 3
     }
     ```
3. **Money & Timestamps (`CLAUDE.md` §5, §7):**
   - Monetary values are serialized in JSON as exact decimal strings (e.g., `"500000.00"`) backed by Java `BigDecimal` and PostgreSQL `numeric(19,2)` to prevent IEEE-754 floating-point precision loss in JavaScript.
   - Timestamps are serialized as ISO-8601 UTC strings (`"2026-11-24T09:15:00Z"`).
4. **Idempotency (`CLAUDE.md` §7, `NFR-004`):**
   - `POST /api/v1/storefront/orders` and `POST /api/v1/admin/payments/{paymentId}/refunds` require the `Idempotency-Key` HTTP header. Replaying the same key with the same payload returns the stored response; replaying the same key with a different payload returns `409 Conflict` (`IDEMPOTENCY_KEY_PAYLOAD_MISMATCH`).

---

## 2. RFC 9457 Problem Details Error Catalog (`application/problem+json`)

Every error response uses `Content-Type: application/problem+json` and never exposes Java stack traces or SQL statements (`CLAUDE.md` §7, `NFR-008`):

| HTTP Status | `errorCode` | `type` URI Suffix | When Returned | Linked `REQ` / `FR` |
|---|---|---|---|---|
| `400` | `VALIDATION_FAILED` | `/problems/validation-failed` | Bean Validation failure on request fields (includes `errors` map of field names to messages) or invalid query parameter (`size > 100`). | `NFR-001`, `NFR-008` |
| `400` | `IDEMPOTENCY_KEY_REQUIRED` | `/problems/idempotency-key-required` | Missing `Idempotency-Key` header on order placement or refund creation. | `NFR-004` |
| `401` | `AUTH_UNAUTHORIZED` | `/problems/unauthorized` | Missing, expired, or invalid authentication token/session, or invalid login credentials. | `FR-001`, `FR-002` |
| `401` | `WEBHOOK_SIGNATURE_INVALID` | `/problems/webhook-signature-invalid` | Missing or invalid cryptographic signature on `/api/v1/webhooks/payments/{provider}`. Performs zero state changes. | `REQ-009`, `FR-025` |
| `403` | `AUTH_FORBIDDEN_ROLE` | `/problems/forbidden-role` | Authenticated user's role is not permitted for the endpoint (e.g., branch staff calling `POST /api/v1/admin/products`). | `REQ-003`, `FR-006`, `FR-010` |
| `403` | `BRANCH_ACCESS_DENIED` | `/problems/branch-access-denied` | Authenticated branch user targets a `branchId` outside their server-resolved `authorizedBranchIds`. | `REQ-011`, `REQ-012`, `FR-006` |
| `404` | `RESOURCE_NOT_FOUND` | `/problems/resource-not-found` | Requested entity does not exist, **or** Customer A requests Customer B's order ID (returns `404` instead of `403` to avoid leaking existence, `CLAUDE.md` §7). | `FR-002`, `BRULE-027` |
| `409` | `INVENTORY_INSUFFICIENT_STOCK` | `/problems/insufficient-stock` | Requested order, stock transfer dispatch, or spoilage deduction would reduce `available_stock` below zero (`< 0`). | `REQ-007`, `REQ-014`, `FR-015`, `FR-018`, `FR-019`, `FR-021` |
| `409` | `ORDER_PRICE_CHANGED` | `/problems/order-price-changed` | Customer's `confirmedTotalAmount` does not match the processing branch's current price total (`B11`). | `REQ-004`, `REQ-006`, `REQ-017`, `FR-014` |
| `409` | `INVALID_STATE_TRANSITION` | `/problems/invalid-state-transition` | Attempt to transition an order or stock transfer to an invalid state (e.g., cancelling an order already in `PREPARING` or `COMPLETED`). | `FR-018`, `FR-022`, `FR-024` |
| `409` | `DUPLICATE_RESOURCE` | `/problems/duplicate-resource` | Duplicate branch code, duplicate product SKU, duplicate staff/customer email, or refund exceeding paid amount. | `FR-004`, `FR-007`, `FR-010`, `FR-027` |
| `409` | `IDEMPOTENCY_KEY_PAYLOAD_MISMATCH` | `/problems/idempotency-key-mismatch` | Same `Idempotency-Key` reused with a different request payload hash. | `NFR-004` |
| `422` | `DELIVERY_OUT_OF_RANGE` | `/problems/delivery-out-of-range` | Delivery coordinates fall outside the `max_delivery_radius_km` of all active branches. | `REQ-014`, `FR-008`, `FR-021` |
| `500` | `INTERNAL_ERROR` | `/problems/internal-error` | Unexpected server error. Returns only a safe message and `requestId` (never stack traces). | `NFR-008`, `NFR-015` |

---

## 3. API Endpoint Authorization Matrix

| Endpoint & Method | Required Role(s) | Server-Side Branch / Data Scope Enforcement (`REQ-011`, `REQ-012`) |
|---|---|---|
| `GET /api/v1/storefront/branches` | Public | Returns only `status = 'ACTIVE'` branches (`FR-009`). |
| `GET /api/v1/storefront/categories` | Public | Returns only `status = 'ACTIVE'` categories (`FR-010`). |
| `GET /api/v1/storefront/branches/{branchId}/products` | Public | Returns active products with `branchId`'s prices and `available_stock` (`REQ-004`, `REQ-017`). |
| `POST /api/v1/storefront/auth/register` & `/login` | Public | Issues customer token scoped to `customer.id` (`FR-002`). |
| `GET /api/v1/storefront/customers/me` | `CUSTOMER` | Scoped strictly to `authenticatedCustomerId` (`BRULE-027`). |
| `POST /api/v1/storefront/orders/quote` & `POST /orders` | `CUSTOMER` | Ignores any client `branchId` on `DELIVERY` orders (`REQ-012`, `REQ-014`); allocates nearest active branch with stock. |
| `GET /api/v1/storefront/orders` & `GET /orders/{orderId}` | `CUSTOMER` | Filters by `customer_id = authenticatedCustomerId`; returns `404` if `orderId` belongs to another customer (`BRULE-027`). |
| `POST /api/v1/storefront/orders/{orderId}/cancel` | `CUSTOMER` | Verifies `order.customerId == authenticatedCustomerId` and `order.status IN ('PENDING_PAYMENT', 'CONFIRMED')` (`FR-024`). |
| `POST /api/v1/webhooks/payments/{provider}` | Webhook HMAC Signature | Verifies `X-Payment-Signature` HMAC-SHA256 over raw body (`REQ-009`) and deduplicates `(provider, eventId)` (`REQ-010`). |
| `POST /api/v1/admin/auth/login` & `/logout` | Public / Any Staff | Resolves staff user's roles and `authorizedBranchIds` from DB (`FR-001`). |
| `GET /api/v1/admin/branches` | Any Staff | Global roles see all branches; branch roles see assigned branches (`FR-007`). |
| `POST /api/v1/admin/branches` & `PUT /branches/{branchId}` | `SYSTEM_OWNER`, `HEAD_OFFICE_ADMIN` | Global Head Office scope required (`FR-007`, `FR-008`). |
| `GET /api/v1/admin/staff`, `POST /staff`, `PUT /staff/{id}/assignments` | `SYSTEM_OWNER`, `HEAD_OFFICE_ADMIN` | Global Head Office scope required; writes `ROLE_CHANGE` audit entry (`FR-004`, `FR-005`). |
| `GET /api/v1/admin/products` | Any Staff | Read-only central catalog view (`REQ-003`). |
| `POST /api/v1/admin/products` | `SYSTEM_OWNER`, `HEAD_OFFICE_ADMIN` | Central catalog creation restricted to Head Office (`REQ-003`, `FR-010`). |
| `PUT /api/v1/admin/branches/{branchId}/prices/{productId}` | `SYSTEM_OWNER`, `HEAD_OFFICE_ADMIN`, `BRANCH_MANAGER` | `BranchAuthorizationGuard.assertCanAccessBranch(branchId)` (`REQ-004`, `REQ-011`, `REQ-012`, `FR-013`). |
| `GET /api/v1/admin/branches/{branchId}/inventory` & `/movements` | `SYSTEM_OWNER`, `HEAD_OFFICE_ADMIN`, `BRANCH_MANAGER`, `INVENTORY_STAFF`, `SALES_STAFF` | `BranchAuthorizationGuard.assertCanAccessBranch(branchId)` (`REQ-002`, `REQ-008`, `REQ-011`, `REQ-012`). |
| `POST /api/v1/admin/branches/{branchId}/goods-receipts` | `SYSTEM_OWNER`, `HEAD_OFFICE_ADMIN`, `BRANCH_MANAGER`, `INVENTORY_STAFF` | `BranchAuthorizationGuard.assertCanAccessBranch(branchId)` (`REQ-011`, `REQ-012`, `FR-020`). |
| `POST /api/v1/admin/branches/{branchId}/stock-adjustments` | `SYSTEM_OWNER`, `HEAD_OFFICE_ADMIN`, `BRANCH_MANAGER`, `INVENTORY_STAFF` | `BranchAuthorizationGuard.assertCanAccessBranch(branchId)` + `available_stock + delta >= 0` (`REQ-007`, `REQ-011`, `FR-019`). |
| `GET /api/v1/admin/stock-transfers` & `POST /stock-transfers` | `SYSTEM_OWNER`, `HEAD_OFFICE_ADMIN`, `BRANCH_MANAGER`, `INVENTORY_STAFF` | Scoped to transfers where `source_branch_id` or `target_branch_id` is in `authorizedBranchIds` (`REQ-011`, `REQ-015`). |
| `POST /api/v1/admin/stock-transfers/{transferId}/dispatch` | `SYSTEM_OWNER`, `HEAD_OFFICE_ADMIN`, `BRANCH_MANAGER`, `INVENTORY_STAFF` | Must be authorized for **`transfer.sourceBranchId`** (`REQ-011`, `REQ-012`, `FR-018`). |
| `POST /api/v1/admin/stock-transfers/{transferId}/receive` | `SYSTEM_OWNER`, `HEAD_OFFICE_ADMIN`, `BRANCH_MANAGER`, `INVENTORY_STAFF` | Must be authorized for **`transfer.targetBranchId`** (`REQ-011`, `REQ-012`, `FR-018`). |
| `GET /api/v1/admin/orders` & `GET /orders/{orderId}` | `SYSTEM_OWNER`, `HEAD_OFFICE_ADMIN`, `BRANCH_MANAGER`, `SALES_STAFF`, `DELIVERY_STAFF` | Filtered strictly by `processing_branch_id IN (:authorizedBranchIds)` (`REQ-005`, `REQ-011`, `REQ-012`). |
| `POST /api/v1/admin/orders/{orderId}/status` | `SYSTEM_OWNER`, `HEAD_OFFICE_ADMIN`, `BRANCH_MANAGER`, `SALES_STAFF`, `DELIVERY_STAFF` | Must be authorized for `order.processingBranchId` (`REQ-011`, `REQ-012`, `FR-022`). |
| `POST /api/v1/admin/orders/{orderId}/reassign-branch` | `SYSTEM_OWNER`, `HEAD_OFFICE_ADMIN` | Global Head Office scope; moves reservation atomically (`FR-023`). |
| `POST /api/v1/admin/payments/{paymentId}/refunds` | `SYSTEM_OWNER`, `HEAD_OFFICE_ADMIN`, `BRANCH_MANAGER` | Must be authorized for the order's `processingBranchId` (`FR-027`, `BRULE-025`). |
| `GET /api/v1/admin/reports/revenue` & `GET /admin/audit-logs` | `SYSTEM_OWNER`, `HEAD_OFFICE_ADMIN`, `BRANCH_MANAGER` | Branch Manager restricted to own `authorizedBranchIds` (`FR-034`, `FR-036`). |

---

## 4. Concrete Example Requests and Responses

### 4.1 Place Online Home Delivery Order (`POST /api/v1/storefront/orders`)

**Request:**
```http
POST /api/v1/storefront/orders HTTP/1.1
Authorization: Bearer <customer_jwt>
Idempotency-Key: 9b2f4e10-7c1a-4b9e-8a3f-123456789abc
Content-Type: application/json

{
  "fulfilmentType": "DELIVERY",
  "paymentMethod": "ONLINE_GATEWAY",
  "recipientName": "Nguyen Thi Lan",
  "recipientPhone": "0909123456",
  "deliveryStreetAddress": "123 Nguyen Hue",
  "deliveryDistrict": "District 1",
  "deliveryCity": "Ho Chi Minh City",
  "deliveryLatitude": 10.7731,
  "deliveryLongitude": 106.7030,
  "confirmedTotalAmount": "950000.00",
  "items": [
    {
      "productId": "550e8400-e29b-41d4-a716-446655440010",
      "quantity": 2
    }
  ]
}
```

**Response (`201 Created`):**
```json
{
  "id": "7f9c2d10-3a4b-4c5d-8e6f-112233445566",
  "orderNumber": "ORD-20261124-0001",
  "customerId": "11111111-2222-3333-4444-555555555555",
  "processingBranchId": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeee0001",
  "fulfilmentType": "DELIVERY",
  "status": "PENDING_PAYMENT",
  "paymentStatus": "UNPAID",
  "itemsSubtotal": "900000.00",
  "deliveryFee": "50000.00",
  "discountAmount": "0.00",
  "totalAmount": "950000.00",
  "currencyCode": "VND",
  "recipientName": "Nguyen Thi Lan",
  "recipientPhone": "0909123456",
  "deliveryStreetAddress": "123 Nguyen Hue",
  "deliveryDistrict": "District 1",
  "deliveryCity": "Ho Chi Minh City",
  "lines": [
    {
      "productId": "550e8400-e29b-41d4-a716-446655440010",
      "skuSnapshot": "BOUQUET-ROSE-RED-01",
      "productNameSnapshot": "Red Rose Luxury Bouquet",
      "unitPriceSnapshot": "450000.00",
      "quantity": 2,
      "lineTotalSnapshot": "900000.00"
    }
  ],
  "createdAt": "2026-11-24T09:30:00Z",
  "updatedAt": "2026-11-24T09:30:00Z"
}
```

### 4.2 Cross-Branch Access Denied Example (`403 Forbidden` — `REQ-011`, `REQ-012`)

When a Branch Manager assigned only to Branch A (`aaaaaaaa-bbbb-cccc-dddd-eeeeeeee0001`) attempts to adjust stock at Branch B (`aaaaaaaa-bbbb-cccc-dddd-eeeeeeee0002`):

```http
HTTP/1.1 403 Forbidden
Content-Type: application/problem+json

{
  "type": "https://flowerchain.com/problems/branch-access-denied",
  "title": "Branch access denied",
  "status": 403,
  "detail": "You are not authorized to access or modify resources belonging to branch aaaaaaaa-bbbb-cccc-dddd-eeeeeeee0002.",
  "instance": "/api/v1/admin/branches/aaaaaaaa-bbbb-cccc-dddd-eeeeeeee0002/stock-adjustments",
  "errorCode": "BRANCH_ACCESS_DENIED",
  "requestId": "req_01JDF9A8B7C6",
  "timestamp": "2026-11-24T09:35:12Z"
}
```

### 4.3 Payment Webhook Verification & Idempotent Response (`POST /api/v1/webhooks/payments/sandbox-hmac`)

**Request (`REQ-009`, `REQ-010`):**
```http
POST /api/v1/webhooks/payments/sandbox-hmac HTTP/1.1
X-Payment-Signature: sha256=8f4e2a9c1d3b5e7f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f
Content-Type: application/json

{
  "eventId": "evt_20261124_9001",
  "transactionId": "txn_20261124_7788",
  "orderId": "7f9c2d10-3a4b-4c5d-8e6f-112233445566",
  "status": "CAPTURED",
  "amount": "950000.00",
  "currencyCode": "VND",
  "occurredAt": "2026-11-24T09:31:05Z"
}
```

**Response on First Delivery (`200 OK`):**
```json
{
  "eventId": "evt_20261124_9001",
  "outcome": "PROCESSED"
}
```

**Response on Duplicate Retry Delivery (`200 OK` — zero duplicate side effects):**
```json
{
  "eventId": "evt_20261124_9001",
  "outcome": "ALREADY_PROCESSED_IDEMPOTENT"
}
```
