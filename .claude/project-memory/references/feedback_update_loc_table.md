---
name: "Update README LOC table after code/config changes"
description: "Refresh the 'Lines of code' table in README.md whenever Java sources or configuration files in any module are modified"
type: feedback
---

# Update README LOC table after code/config changes

After modifying any source code (`.java`) or configuration
file (`pom.xml`, `compose.yaml`, `application.yaml`,
`schema.sql`, `Dockerfile`, `.properties`, etc.) in any
of the five modules, regenerate **both** artifacts in the
`## Lines of code` section of `README.md`:

1. The Markdown table (LOC column).
2. The Mermaid `xychart-beta` bar chart's `bar [...]`
   values — they must match the table exactly.

**Why:** The LOC table is a key teaching artifact of the
tutorial. It quantifies the relative complexity of the
five architectures (monolith → microservices → 2PC →
messaging → temporal) and supports the narrative that
durable execution simplifies code versus hand-rolled
distributed-transaction patterns. Stale numbers undermine
that argument.

**How to apply:**

- Count non-comment, non-blank lines per module across
  `.java`, `.xml`, `.yaml`/`.yml`, `.sql`, `.properties`,
  and `Dockerfile` files. Exclude `target/`, `.idea/`,
  `node_modules/`.
- Strip comments per language: `//` and `/* */` for Java;
  `#` for YAML/properties/Dockerfile; `<!-- -->` for XML;
  `--` and `/* */` for SQL.
- Update each row's LOC value in the markdown table.
- Preserve column alignment: col1 = 22 chars wide
  (module name in backticks), col2 = 6 chars right-aligned
  via `---:` separator, col3 = 44 chars (Notes). Total
  line width = 76 chars.
- If a module's relative size changes meaningfully (e.g.,
  no longer ~2× the monolith), revise the takeaway
  bullets below the table accordingly.
- The Notes column describes architectural limitations,
  not size — only revise it if the module's failure
  semantics change (e.g., adding compensation to module 4
  would warrant a new note).
