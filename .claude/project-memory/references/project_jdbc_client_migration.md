---
name: "Migration from JPA/Hibernate to Spring JdbcClient"
description: "All four modules migrate off JPA to Spring's native JdbcClient; module 1 done, modules 2-4 still on JPA"
type: project
---

# Migration from JPA/Hibernate to Spring JdbcClient

The tutorial is migrating off `spring-boot-starter-data-jpa` to
`spring-boot-starter-jdbc` + `JdbcClient`. Domain types become
immutable Java records. Repositories become `@Repository` classes
wrapping `JdbcClient`. Schema is a checked-in `schema.sql`
(replaces `ddl-auto: update`). `data.sql` uses
`ON CONFLICT (id) DO NOTHING` so restarts against a persisted
volume don't crash.

**Status (2026-05-06):**
- `1-monolith` — fully migrated.
- `2-microservices`, `3-messaging`, `4-temporal` — still on JPA,
  to be migrated later in the same style.

**Why:** the tutorial's pedagogical goal is to make SQL and
transactional boundaries *visible*. JPA's dirty checking,
`@Lock`, `@PrePersist`, and `ddl-auto: update` all hide what the
tutorial is trying to teach (e.g. `SELECT … FOR UPDATE`, the
explicit `UPDATE` after a balance change, the schema itself).
`JdbcClient` keeps every SQL statement in the source code.

**How to apply:**
- When migrating a remaining module, follow the module-1
  template: record domain types, `@Repository` class with
  `JdbcClient` (constructor-injected), `findByIdForUpdate` for
  the row lock, explicit `updateBalance` (no dirty checking),
  `schema.sql` next to `data.sql`, idempotent inserts.
- `@Transactional` boundaries stay on services, identical to
  the JPA version — Spring TX is JPA-independent.
- Keep the teaching comment around the row-lock query — it is
  the core ACID lesson of module 1.
- Do not reintroduce Spring Data JDBC as a middle ground; it
  was considered and rejected (still hides things behind
  `CrudRepository`).
