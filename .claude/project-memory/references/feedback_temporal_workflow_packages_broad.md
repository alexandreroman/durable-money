---
name: "Keep workflow-packages: io.temporal.demos broad"
description: "Use the wider scan root for Temporal workflow auto-discovery, not a tighter sub-package"
type: feedback
---

# Keep workflow-packages: io.temporal.demos broad

In `4-temporal/workflow/src/main/resources/application.yaml`,
the Temporal Spring Boot starter is configured with:

```yaml
spring:
  temporal:
    workers-auto-discovery:
      workflow-packages: io.temporal.demos
```

Keep this broad scan root. Do not tighten it to
`io.temporal.demos.durablemoney.workflow` (or any sub-package
that targets only the current workflow class).

**Why:** The user prefers the wider scope so additional
workflow/activity classes added under any
`io.temporal.demos.*` sub-package are picked up automatically
without further config edits. The reduced "blast radius" of a
narrower package is not worth the maintenance cost when
adding new examples.

**How to apply:** Treat the package value as an intentional
choice. If sweeping configuration for tightening, leave this
key alone. Apply the same broad-scope pattern if introducing
similar starter-driven scans elsewhere.
