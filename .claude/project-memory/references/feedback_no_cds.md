---
name: "Do not add CDS to Dockerfiles"
description: "Skip Class Data Sharing (CDS) training runs in module Dockerfiles even when proposing startup optimizations"
type: feedback
---

# Do not add CDS to Dockerfiles

Do not propose or add a Spring Boot CDS
(Class Data Sharing) training run to any
module's `Dockerfile`. This includes
`-XX:ArchiveClassesAtExit`, `-XX:SharedArchiveFile`,
`-Dspring.context.exit=onRefresh` patterns, or
similar AppCDS variants.

**Why:** the user explicitly rejected adding
CDS to this repo when it came up during a
Dockerfile optimization review. The training
run adds build-time complexity (needs a
runnable context — usually means a build-only
profile that mocks the datasource) for a
benefit that is not worth it in this tutorial
codebase. Tutorial clarity beats marginal
startup gains.

**How to apply:** when reviewing or editing
Dockerfiles in any module, do NOT suggest CDS
as an optimization. Other startup
optimizations (AOT processing, JVM flags,
`JAVA_TOOL_OPTIONS`) remain on the table —
this rule is specific to CDS.
