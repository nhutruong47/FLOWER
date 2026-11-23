# Process: online order fulfilment

- **Status:** DRAFT
- **Last updated:** 2026-11-23
- **Owner:** business-analyst · **Task:** TASK-006
- **Sources:** owner brief, 2026-11-23 (baseline rules REQ-001 to REQ-012); owner answers, 2026-11-23 (B3a, B3b, B9).
- **Labels:** CONFIRMED (stated by the owner; source given) · INFERRED (follows from a source but was not stated) · NEEDS CONFIRMATION (open question given) · NOT FOUND (no source at all).

This process runs from a customer ordering on the storefront to the order reaching the customer. Gaps are left visible on purpose. Requirements are in [REQUIREMENTS.md](../../requirements/REQUIREMENTS.md). Questions are in [open-questions.md](../../requirements/open-questions.md). Terms are in the [glossary](../glossary.md).

## Actors

| Actor | Part in this process | Label |
|---|---|---|
| Customer | Places the order. Chooses delivery or pickup. | CONFIRMED (B3a) |
| System | Selects the processing branch for delivery orders. | CONFIRMED (B3b) |
| Processing branch | Handles the order. | Every order has one: CONFIRMED (REQ-005). Which staff do what: NEEDS CONFIRMATION (B5) |
| Delivery person | Takes delivery orders to the address. | NEEDS CONFIRMATION (B15) |
| Payment provider | Confirms online payments. | NEEDS CONFIRMATION (B4) |

## Trigger

A customer places an order on the storefront. CONFIRMED (owner brief: customer website; B3a).

## Steps

The order of steps 5 to 9 is NEEDS CONFIRMATION (B4, B13).

| # | Step | Label |
|---|---|---|
| 1 | The customer browses products. The storefront shows the prices of one branch. | Prices are per branch: CONFIRMED (B9). Which branch, before one is known: NEEDS CONFIRMATION (B11) |
| 2 | The customer chooses products and quantities and goes to checkout. | INFERRED (needed to place any order; no cart or checkout rules given) |
| 3 | The customer chooses home delivery or in-store pickup. | CONFIRMED (B3a) |
| 4a | Delivery: the customer gives the delivery address. | CONFIRMED (B3b refers to "the delivery address"). Fee, date and time: NEEDS CONFIRMATION (B15) |
| 4b | Pickup: a store is chosen for pickup. | Who chooses: NEEDS CONFIRMATION (B3c). That this store is the processing branch: INFERRED, to confirm in B3c. Pickup time: NEEDS CONFIRMATION (B15) |
| 5 | Delivery: the system selects the processing branch. It is the branch nearest the delivery address that still has enough stock for the order. The customer does not choose it. | CONFIRMED (B3b). How "nearest" is measured: NEEDS CONFIRMATION (B3e) |
| 6 | The order records its processing branch. | CONFIRMED (REQ-005) |
| 7 | The order keeps a snapshot of the product data and prices at order time. | CONFIRMED (REQ-006). Which branch's price is used: NEEDS CONFIRMATION (B11) |
| 8 | Stock for the order is reserved or deducted at the processing branch. Available stock never goes below zero, and each change is recorded. | Never negative, recorded: CONFIRMED (REQ-007, REQ-008). When this happens: NEEDS CONFIRMATION (B13) |
| 9 | Payment. An online payment counts only after the server verifies the provider's notification, and each payment is processed once. | Verification and once-only: CONFIRMED (REQ-009, REQ-010). Payment methods and timing: NEEDS CONFIRMATION (B4, B13) |
| 10 | Staff at the processing branch prepare the order. | INFERRED (the processing branch handles the order, REQ-005). Who does what: NEEDS CONFIRMATION (B5) |
| 11a | Delivery: the order is delivered to the address. | Delivery is offered: CONFIRMED (B3a). Who delivers: NEEDS CONFIRMATION (B15) |
| 11b | Pickup: the customer collects the order at the store. | CONFIRMED (B3a) |
| 12 | The order moves through statuses until it is delivered or collected. | NEEDS CONFIRMATION (B17) |

## Exceptions

| # | Exception | Label |
|---|---|---|
| E1 | No single branch has enough stock for the whole delivery order, or no branch has an item at all. | NEEDS CONFIRMATION (B3d) |
| E2 | The delivery address is far from every branch. | NEEDS CONFIRMATION (B3e) |
| E3 | Two branches are equally near the delivery address. | NEEDS CONFIRMATION (B3e) |
| E4 | Two customers order the last units at the same branch at the same time. | Stock never goes below zero: CONFIRMED (REQ-007). What the second customer gets: NEEDS CONFIRMATION (B13, B3d) |
| E5 | The pickup store does not have enough stock. | NEEDS CONFIRMATION (B3c, B3d) |
| E6 | The selected branch cannot fulfil the order after all. | NEEDS CONFIRMATION (B14) |
| E7 | Payment fails or is never confirmed. | NEEDS CONFIRMATION (B4, B13) |
| E8 | The payment provider sends the same notification twice. | It is processed once: CONFIRMED (REQ-010) |
| E9 | The customer or staff cancel the order. | NEEDS CONFIRMATION (B17) |

## Related requirements

REQ-004, REQ-005, REQ-006, REQ-007, REQ-008, REQ-009, REQ-010, REQ-013, REQ-014, REQ-017.
