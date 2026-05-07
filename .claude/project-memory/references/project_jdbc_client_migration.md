---
name: "Migration from JPA/Hibernate to Spring JdbcClient"
description: "All four modules use Spring's native JdbcClient; the JPA/Hibernate migration is fully complete"
type: project
---

# Migration from JPA/Hibernate to Spring JdbcClient

The tutorial uses `spring-boot-starter-jdbc` + `JdbcClient` (not
`spring-boot-starter-data-jpa`). Domain types are immutable Java
records. Repositories are `@Repository` classes wrapping
`JdbcClient`. Schema is a checked-in `schema.sql` (no
`ddl-auto: update`). `data.sql` uses `ON CONFLICT (id) DO NOTHING`
so restarts against a persisted volume don't crash.

**Status:** all four modules fully migrated as of 2026-05-06
(`1-monolith`, `2-microservices/account-service`,
`4-messaging/account-service`, `4-messaging/transfer-service`,
`5-temporal/account-service`). The Temporal worker in
`5-temporal/workflow` and the `2-microservices/transfer-service`
never used JPA.

**Why:** the tutorial's pedagogical goal is to make SQL and
transactional boundaries *visible*. JPA's dirty checking,
`@Lock`, `@PrePersist`, and `ddl-auto: update` all hide what the
tutorial is trying to teach (e.g. `SELECT … FOR UPDATE`, the
explicit `UPDATE` after a balance change, the schema itself).
`JdbcClient` keeps every SQL statement in the source code.

**How to apply:**
- `@Transactional` boundaries stay on services, identical to
  the JPA version — Spring TX is JPA-independent.
- Keep the teaching comment around the row-lock query — it is
  the core ACID lesson of module 1.
- Do not reintroduce Spring Data JDBC as a middle ground; it
  was considered and rejected (still hides things behind
  `CrudRepository`).
