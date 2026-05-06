---
name: "Nest types inside their owner class"
description: "Narrowest scope by default — nest records, enums, and helper classes inside the single class that uses them; top-level only when shared across the package"
type: feedback
---

# Nest types inside their owner class

The user wants the **narrowest possible scope**
for every code element. Records, enums, and
helper types that are used by a single class
must be **nested** inside that class
(package-private). Top-level types are reserved
for the cases where multiple classes in the
package legitimately share them.

**Why:** the user is not a fan of top-level
records and asked for this principle to apply
generally — not only to controller view records.
A previous extraction of `TransferResult` to a
top-level record in
`2-microservices/transfer-service` was tried
and rejected; it was moved back into
`TransferService` as a nested record.

**How to apply:**
- A controller's request/response records
  (e.g. `NewTransfer`, `TransferView`) stay
  nested inside the `@RestController`.
- A service's result/parameter records (e.g.
  `TransferResult`) stay nested inside the
  `@Service` class — even if the controller
  consumes them; the controller references the
  qualified name `MyService.MyResult` (no
  cyclical dependency, since the controller
  already depends on the service).
- A client's request DTO (e.g.
  `AccountClient.DebitCreditRequest`) stays
  nested as a `private` record inside the
  client component.
- Top-level types are justified only when
  multiple sibling classes in the package use
  them. Examples in this repo: `Account`,
  `Transfer`, `TransferStatus`,
  `AccountCommandMessage`,
  `AccountResultMessage`, `CommandType`,
  `InsufficientFundsException` — each is read
  by ≥ 2 collaborators in its package.
- When in doubt, start nested; promote to
  top-level only when a second consumer
  appears.

This complements the existing visibility rules
from `skillbox:java-rules` and
`skillbox:spring-boot-rules` (package-private
classes, narrowest modifiers) by extending them
to type **placement**, not just access
modifiers.
