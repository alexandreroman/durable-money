---
name: "Keep spring-boot-devtools in every module"
description: "Devtools stays as a runtime/optional dependency in every pom.xml"
type: feedback
---

# Keep spring-boot-devtools in every module

Every module's `pom.xml` declares
`spring-boot-devtools` with `<scope>runtime</scope>` and
`<optional>true</optional>`. Keep it. Do not propose removing
it as "useless in containers" or "dead weight in the layered
jar".

**Why:** The user wants devtools available for the local IDE
workflow (live reload, restart). Devtools auto-disables when
the app runs from a packaged jar in production mode, so the
runtime cost in the container is effectively zero. The
ergonomic gain in the IDE outweighs the few KB in the
dependencies layer.

**How to apply:** When triaging unused dependencies, exclude
`spring-boot-devtools`. If a new module is added, copy the
same dependency block (runtime + optional) from a sibling
module.
