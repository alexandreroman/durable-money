---
name: "Temporal Spring Boot starter: activities need @Component AND register-activity-beans: true"
description: "@ActivityImpl alone won't register activities under temporal-spring-boot-starter; the class must also be a Spring bean and the auto-discovery flag must be enabled"
type: feedback
---

# Temporal Spring Boot starter: activities need @Component AND register-activity-beans: true

When using `io.temporal:temporal-spring-boot-starter`,
two conditions must BOTH hold for an activity to be
registered with the worker:

1. The activity implementation class must be a Spring
   bean (`@Component`, `@Service`, or registered via
   `@Bean`). The `@ActivityImpl(workers = "...")`
   annotation alone does NOT register the class as a
   bean — it only tags an existing bean for the
   worker.
2. `spring.temporal.workers-auto-discovery.register-activity-beans:
   true` must be set in `application.yaml`. The
   default is `false`/`null`.

Symptom when either is missing: the workflow runs and
schedules the first activity, which then sits in
`Scheduled` state forever — no activity worker polls
because none was registered. The startup logs show a
"Registering auto-discovered workflow class" line for
each workflow but no equivalent "Registering
auto-discovered activity bean" line.

**Why:** verified against the SDK source on master.
`WorkersAutoDiscoveryProperties` has separate
`workflowPackages` and `registerActivityBeans` fields
— the former only scans for `@WorkflowImpl`, the
latter gates activity bean discovery via
`if (autoDiscovery.isRegisterActivityBeans())` in
`WorkersTemplate`. The deprecated `packages` property
used to do both at once; the split is the new
behavior. This is a footgun: the old form silently
worked, the new form silently does nothing if you
forget the flag.

**How to apply:** in module 4 (or any future Temporal
Spring Boot work in this repo), every activity impl
class needs `@Component` next to `@ActivityImpl`, and
the workers auto-discovery block in
`application.yaml` must include
`register-activity-beans: true`.
