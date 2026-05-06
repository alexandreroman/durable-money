---
name: "Reference nested types by simple name"
description: "Import nested records/enums so they read as DebitInput, not AccountActivities.DebitInput"
type: feedback
---

# Reference nested types by simple name

When code references a nested record, class, or enum (for
example `AccountActivities.DebitInput`), add a regular type
import for the nested type and use the simple name in the
body:

```java
import io.temporal.demos.durablemoney.workflow.AccountActivities.DebitInput;
...
var input = new DebitInput(accountId, amount, transferId);
```

Do NOT keep the `OuterClass.NestedType` qualifier in the body
once the import is in place. Use a regular `import`, not
`import static` — these are types, and a regular import is
the idiomatic Java form even though both compile.

For nested types declared on an interface that the current
class **implements** (for instance `TransferWorkflow.Input`
and `TransferWorkflow.Result` inside `TransferWorkflowImpl`),
no import is needed: the implements clause already brings
them into scope, so reference them as `Input` / `Result`
directly.

**Why:** The user finds the qualified form noisy in method
bodies. With the import lifted to the top of the file the
reader sees the type origin in one place and method bodies
read cleanly. This pairs with the existing "narrowest scope"
rule on nested types — they're nested for ownership, not for
readability at every call site.

**How to apply:** Whenever a method body refers to
`OuterClass.NestedType` more than once, add a type import for
the nested type and drop the qualifier. When implementing an
interface that has nested record/enum members, use them
unqualified inside the implementing class — do not import
them and do not prefix with the interface name.
