# Business analysis

- **Owner:** business-analyst · **Phase:** 2 (P03 Business Analysis) · **Status:** DRAFT (complete P03 analysis package awaiting owner answers to open questions in [../requirements/open-questions.md](../requirements/open-questions.md)).
- **Last updated:** 2026-11-24

This folder holds the comprehensive P03 Business Analysis deliverables for FlowerChain:

1. **[business-overview-and-model.md](business-overview-and-model.md):** Business Overview, Value Proposition, and analysis across all 17 domain areas (ownership, stakeholders, head office vs. branch operations, catalog/variants/seasonal items, inventory, suppliers/receiving, sales/orders, customers, payments/refunds, delivery/fulfilment, promotions/vouchers, employees/branches, inter-branch transfers, spoilage/adjustments, reporting, notifications/support, and audit controls).
2. **[stakeholders-and-actors.md](stakeholders-and-actors.md):** Stakeholder Analysis, organizational hierarchy, actor catalog, and proposed role & branch-scope permission matrix (for owner confirmation under `B5`).
3. **[business-process-maps.md](business-process-maps.md):** Structured Business Process Maps (`WF-01` to `WF-12`) with actors, triggers, preconditions, main flows, alternatives, exceptions, and outcomes:
   - `WF-01`: Customer purchase (browsing, cart, checkout initiation)
   - `WF-02`: Branch selection (delivery vs. pickup)
   - `WF-03`: Order allocation (automatic nearest-branch selection with stock)
   - `WF-04`: Inventory availability and reservation
   - `WF-05`: Payment and server-side webhook verification
   - `WF-06`: Order fulfillment (branch preparation)
   - `WF-07`: Delivery or in-store pickup hand-over
   - `WF-08`: Cancellation and refund
   - `WF-09`: Goods receiving (branch stock-in)
   - `WF-10`: Stock adjustment and spoilage write-off
   - `WF-11`: Inter-branch stock transfer
   - `WF-12`: Product return / post-delivery resolution
   - Detailed narrative supplements in `processes/`:
     - [processes/online-order-fulfilment.md](processes/online-order-fulfilment.md)
     - [processes/stock-transfer.md](processes/stock-transfer.md)
     - [processes/walk-in-sale-scope.md](processes/walk-in-sale-scope.md)
4. **[use-case-catalog.md](use-case-catalog.md):** Catalog of Customer Storefront (`UC-CUST-01`..`08`), Branch Operations (`UC-BR-01`..`10`), and Head Office (`UC-HO-01`..`08`) use cases traced to workflows and requirements.
5. **[business-rules-catalog.md](business-rules-catalog.md):** Catalog of business rules (`BRULE-001` to `BRULE-030`) covering global, catalog/pricing, inventory/transfers/spoilage, order/allocation/fulfilment, payment/refund, and security/branch-authorization rules.
6. **[glossary.md](glossary.md):** Business Glossary defining core domain terms with sources and open questions.
7. **[assumptions-constraints-and-risks.md](assumptions-constraints-and-risks.md):** Confirmed Constraints, Flagged Working Assumptions (`ASM-01`..`05`), and Business/Technical Risk Register (`RISK-01`..`07`).

Every material statement is classified as **CONFIRMED**, **INFERRED**, **PROPOSED**, or **NEEDS CONFIRMATION**. Requirements are registered in [../requirements/REQUIREMENTS.md](../requirements/REQUIREMENTS.md) and open questions requiring owner decisions are tracked in [../requirements/open-questions.md](../requirements/open-questions.md).
