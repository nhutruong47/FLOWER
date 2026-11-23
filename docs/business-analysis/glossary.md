# Glossary

- **Status:** DRAFT. No definition is approved yet.
- **Last updated:** 2026-11-23
- **Owner:** business-analyst · **Task:** TASK-006

Business terms, defined once and used everywhere. This first pass contains only terms that appear in the owner's baseline rules (REQ-001 to REQ-012, owner brief 2026-11-23) or in the owner's answers of 2026-11-23 (B2, B3a, B3b, B9). Requirements are in [REQUIREMENTS.md](../requirements/REQUIREMENTS.md). Open points link to [open-questions.md](../requirements/open-questions.md).

| Term | Definition (DRAFT) | Source | Open points |
|---|---|---|---|
| Branch | One flower shop of the business. One business owns several branches. | REQ-001 | – |
| Branch price | The price of a product at one branch. Each branch has its own prices. | B9, REQ-004 | Who sets it, and a product with no price: B9b. Which branch's price the storefront shows: B11 |
| Delivery order | An online order that is delivered to an address the customer gives. | B3a, B3b | Who delivers, fee, date and time: B15 |
| Order snapshot | A copy of the product data and price stored on an order when it is placed. Later catalog or price changes do not change it. | REQ-006 | Which branch's price is copied: B11 |
| Pickup order | An online order that the customer collects at a store. | B3a | Which store and who chooses it: B3c. That the store is one of the branches is INFERRED. Pickup time: B15 |
| Processing branch | The branch that handles an order. Every order has one, and it can be traced. For a delivery order, the system picks it: the branch nearest the delivery address that still has enough stock. | REQ-005, B3b | Pickup orders: B3c. "Nearest": B3e. Not enough stock at any one branch: B3d. Moving an order: B14 |
| Stock (inventory) | The quantity of each product that a branch holds. Stock always belongs to one branch; there is no central warehouse. Available stock never goes below zero, and every change is auditable. | REQ-002, REQ-007, REQ-008, B2 | Reserved versus available stock: B13. Stock in transit: B12 |
| Stock transfer | Moving goods from one branch's stock to another branch's stock. Every transfer is recorded. | B2 | Who requests and approves, in transit: B12. Shortfalls, damage, cancellation: B16 |
