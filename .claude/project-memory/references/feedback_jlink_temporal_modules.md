---
name: "Slim jlink JREs for Temporal apps need jdk.management"
description: "Temporal Java SDK Worker static initializer needs com.sun.management.OperatingSystemMXBean from the jdk.management module"
type: feedback
---

# Slim jlink JREs for Temporal apps need jdk.management

Any Dockerfile in this repo that builds a custom
slim JRE via `jlink` for a service running the
Temporal Java SDK MUST include `jdk.management` in
the `--add-modules` list.

**Why:** the Temporal Worker's static initializer
instantiates `io.temporal.worker.tuning.JVMSystemResourceInfo`,
which references `com.sun.management.OperatingSystemMXBean`.
That class is in the `jdk.management` JDK module —
NOT in `java.management` (which is the platform
module that everyone tends to remember). Without
`jdk.management` on the runtime module path, the
container crash-loops at boot with
`NoClassDefFoundError: com.sun.management.OperatingSystemMXBean`
originating from `Worker.<clinit>` /
`WorkerFactory.newWorker`. The error only surfaced
in this repo after module 4 was migrated to the
Temporal Spring Boot starter — the prior manual SDK
wiring did not trigger this initializer at startup.

**How to apply:** when adding or editing a `jlink
--add-modules` line in any module's Dockerfile for
a service that depends on `io.temporal:*`, ensure
`jdk.management` is in the comma-separated list.
Module 4's `4-temporal/workflow/Dockerfile` is the
current canonical example.
