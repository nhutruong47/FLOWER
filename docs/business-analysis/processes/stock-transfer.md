# Process: stock transfer between branches

- **Status:** DRAFT
- **Last updated:** 2026-11-23
- **Owner:** business-analyst · **Task:** TASK-006
- **Sources:** owner answer, 2026-11-23 (B2); owner brief, 2026-11-23 (REQ-002, REQ-007, REQ-008, REQ-011).
- **Labels:** CONFIRMED (stated by the owner; source given) · INFERRED (follows from a source but was not stated) · NEEDS CONFIRMATION (open question given) · NOT FOUND (no source at all).

Each branch has its own stock. Branches may transfer goods to each other, and transfers are recorded (CONFIRMED, B2). There is no central warehouse, so every transfer goes from one branch to another (CONFIRMED, B2: the central-warehouse option was offered and not chosen). Requirements are in [REQUIREMENTS.md](../../requirements/REQUIREMENTS.md). Questions are in [open-questions.md](../../requirements/open-questions.md). Terms are in the [glossary](../glossary.md).

## Actors

| Actor | Part in this process | Label |
|---|---|---|
| Sending branch | Gives goods from its stock. | CONFIRMED (B2) |
| Receiving branch | Adds the goods to its stock. | CONFIRMED (B2) |
| Requester and approver | Asks for the transfer; approves it. | NEEDS CONFIRMATION (B12) |
| Head office | Any part in transfers. | NEEDS CONFIRMATION (B12, B5) |
| System | Keeps the transfer record and the stock audit trail. | CONFIRMED (B2, REQ-008) |

## Trigger

A branch needs goods that another branch has. INFERRED: the owner did not say why transfers happen. A transfer to fulfil an online order is one option in B3d (NEEDS CONFIRMATION).

## Steps

| # | Step | Label |
|---|---|---|
| 1 | Someone requests a transfer, naming the sending branch, the receiving branch, the products and the quantities. | Who: NEEDS CONFIRMATION (B12). What the request contains: INFERRED (a transfer of goods between two branches needs these) |
| 2 | The transfer is approved. | Whether approval is needed, and by whom: NEEDS CONFIRMATION (B12) |
| 3 | The sending branch sends the goods. Its stock goes down by the quantity sent, and never below zero. | Stock goes down: INFERRED (B2: the goods leave its stock). Never below zero: CONFIRMED (REQ-007). When the stock changes: NEEDS CONFIRMATION (B12) |
| 4 | The goods are on the way. | Whether stock "in transit" is counted anywhere: NEEDS CONFIRMATION (B12) |
| 5 | The receiving branch receives the goods. Its stock goes up by the quantity received. | INFERRED (B2: the goods join its stock). Who confirms receipt: NEEDS CONFIRMATION (B12) |
| 6 | The transfer is recorded. Each stock change at both branches appears in the inventory audit trail. | Transfer recorded: CONFIRMED (B2). Audit trail: CONFIRMED (REQ-008) |

## Exceptions

| # | Exception | Label |
|---|---|---|
| E1 | The sending branch has less stock than requested. | Stock never goes below zero: CONFIRMED (REQ-007). Refuse the transfer or send less: NEEDS CONFIRMATION (B16) |
| E2 | The receiving branch gets fewer goods, or damaged goods. | NEEDS CONFIRMATION (B16) |
| E3 | A transfer is cancelled after it starts. | NEEDS CONFIRMATION (B16) |
| E4 | The approver rejects the request. | NEEDS CONFIRMATION (B12) |

## Notes

- A transfer touches two branches. Branch-level authorization (REQ-011) applies on both sides. Which role may act on each side depends on B5 and B12. INFERRED.

## Related requirements

REQ-002, REQ-007, REQ-008, REQ-011, REQ-015, REQ-016.
