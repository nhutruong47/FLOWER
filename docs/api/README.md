# API

- **Owner:** solution-architect (contract), backend-developer (implementation) · **Phases:** 4, 5 (P06 API Design) and 7 · **Status:** PROPOSED (complete P06 OpenAPI 3.1 contract and error/auth specifications awaiting owner approval before P07).
- **Last updated:** 2026-11-24

This folder holds the comprehensive P06 REST API contract deliverables for FlowerChain:

1. **[openapi.yaml](openapi.yaml):**
   - Complete OpenAPI 3.1.0 specification under `/api/v1` with separate route groups for the Customer Storefront (`/api/v1/storefront/**`), Internal Admin (`/api/v1/admin/**`), and Payment Provider Webhooks (`/api/v1/webhooks/payments/**`).
   - Defines request/response schemas, validation rules, pagination parameters (`page`, `size <= 100`), `Idempotency-Key` headers, decimal string representations for `BigDecimal` money values, and RFC 9457 `ProblemDetail` responses.
2. **[errors-auth-and-examples.md](errors-auth-and-examples.md):**
   - API versioning, pagination, sorting, filtering, money, timestamp, and idempotency conventions (`CLAUDE.md` §7).
   - RFC 9457 Problem Details error catalog (`application/problem+json` error codes and status mappings).
   - Endpoint-by-endpoint Authorization and Server-Side Branch Scope Matrix (`REQ-011`, `REQ-012`).
   - Concrete HTTP request and response examples (online delivery order placement with snapshots, cross-branch `403` rejection, and HMAC-signed idempotent payment webhooks).
3. **[consistency-validation.md](consistency-validation.md):**
   - End-to-end consistency validation matrix verifying 1:1 alignment across Requirements (`REQ-001`..`017`, `FR-001`..`038`), Architecture Modules (`L0`..`L7`), PostgreSQL 16 Tables/Constraints, OpenAPI Endpoints, and Test Cases (`TEST-001`..`020`).
