---
name: "Bind OffsetDateTime, not Instant, with JdbcClient on PostgreSQL"
description: "PostgreSQL JDBC driver cannot infer SQL type for java.time.Instant via setObject; bind .atOffset(ZoneOffset.UTC) instead"
type: feedback
---

# Bind OffsetDateTime, not Instant, with JdbcClient on PostgreSQL

When using Spring `JdbcClient` (or raw `setObject`) against
PostgreSQL, never bind a `java.time.Instant` directly. The
PostgreSQL JDBC driver throws:

```
Can't infer the SQL type to use for an instance of java.time.Instant.
Use setObject() with an explicit Types value to specify the type to use.
```

Bind `OffsetDateTime` (UTC) instead — it maps cleanly to
`TIMESTAMPTZ`:

```java
.params(..., instant.atOffset(ZoneOffset.UTC))
```

Reading is unaffected: `SimplePropertyRowMapper` maps
`TIMESTAMPTZ` columns back to `Instant` record components via
`ResultSet.getObject(idx, Instant.class)`. So domain types
(records, DTOs) stay as `Instant` — only the bind site changes.

**Why:** caught during E2E testing of module 1 after migrating
from JPA/Hibernate (which performed this conversion implicitly)
to `JdbcClient`. `AccountRepository.insert` had the same defect
but escaped detection because seed inserts in `data.sql` use the
SQL `now()` literal rather than a JDBC bind parameter.

**How to apply:** any new `JdbcClient`-based repository in this
project (modules 2–4 if they migrate off JPA) must convert
`Instant` to `OffsetDateTime` at bind time. Do not change record
or column types. Watch for the same trap with `LocalDateTime` →
`TIMESTAMP` if naive timestamps ever appear.
