---
name: "Spring Boot 4 ships Jackson 3 (tools.jackson.*)"
description: "import tools.jackson.databind.ObjectMapper, not com.fasterxml.jackson.databind.ObjectMapper, in Boot 4 modules"
type: project
---

# Spring Boot 4 ships Jackson 3 (tools.jackson.*)

In every Boot 4.0.x module of this repo, the
auto-configured `ObjectMapper` is from Jackson 3
(`tools.jackson.databind.ObjectMapper`). The legacy
Jackson 2 package (`com.fasterxml.jackson.databind`)
is NOT on the compile classpath — only
`com.fasterxml.jackson.core:jackson-annotations`
is dragged in transitively for annotation
compatibility. `JacksonJsonMessageConverter` and
the auto-configured `ObjectMapper` both use
Jackson 3.

API differences worth knowing:
- `JsonNode.asText(String defaultValue)` is
  deprecated in Jackson 3 — use
  `node.isMissingNode() / node.isNull()` guards
  and call `node.asString()`.

**Why:** Boot 4 migrated to Jackson 3 (the
`tools.jackson.*` package). New code that injects
or constructs an `ObjectMapper` must use the new
package or compilation fails with "package
com.fasterxml.jackson.databind does not exist".

**How to apply:** when writing code that touches
JSON in any module under this repo, import
`tools.jackson.databind.ObjectMapper` and use
the Jackson 3 APIs. Verify with
`mvn dependency:tree | grep jackson` if unsure.
