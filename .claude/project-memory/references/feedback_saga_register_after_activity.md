---
name: "Register Saga compensations AFTER the activity completes"
description: "In Temporal Saga workflows, call saga.addCompensation only after the corresponding activity returns successfully — never before"
type: feedback
---

# Register Saga compensations AFTER the activity completes

In Temporal Java Saga workflows, register a step's
compensation **only after** the activity it guards
has actually returned successfully. Do not
pre-register a compensation before invoking the
activity it would undo.

```java
// Correct
activities.debitAccount(debitInput);
saga.addCompensation(activities::reverseDebit, reverseDebitInput);
activities.creditAccount(creditInput); // if this throws, debit gets reversed
```

**Why:** When an activity exhausts its retries it
throws `ActivityFailure`, the catch block calls
`saga.compensate()`, and any pre-registered
compensation runs even though its guarded activity
never succeeded. We hit this concretely in module
4: registering `reverseDebit` before `debitAccount`
caused a failed 5000 transfer to credit the source
account — balance went 800 → 5800 because
`reverseDebit` ran when no debit had ever
occurred.

**How to apply:** When writing or reviewing any
Temporal Saga in this repo (currently `4-temporal`),
the registration line for each step must come
*after* the activity call returns, not before. This
is safe across crashes because Temporal's event
history deterministically replays both the activity
result and the subsequent `addCompensation` call;
crash safety does not require pre-registration. The
common "register before" idiom from generic Saga
tutorials is wrong for Temporal — flag it on
review.
