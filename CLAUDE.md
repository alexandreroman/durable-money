# durable-money

Tutorial comparing four architectures for resilient
money transfer — from ACID monolith to Temporal Saga.

See [README.md](README.md) for full documentation.

## Tech stack

- Java 25, Spring Boot 4.0.5
- PostgreSQL 17 (all modules)
- RabbitMQ 4 (module 4 only)
- Temporal SDK 1.35.0 (module 5 only)
- Docker Compose (each module ships its own)

## Build & run

```bash
# Start any module (from its directory)
docker compose up --build

# Build a single service locally
mvn package -DskipTests
```

## Modules

- `1-monolith` — single service, ACID `@Transactional`
- `2-microservices` — REST calls, no distributed transaction
- `3-two-phase-commit` — hand-rolled 2PC over PostgreSQL prepared transactions
- `4-messaging` — RabbitMQ commands/results with DLQ
- `5-temporal` — Temporal Saga with automatic compensation

## Agents

Use the following agents (from the
[skillbox](https://github.com/alexandreroman/skillbox)
plugin) for all code tasks:

- **code-writer** — for ANY task that writes,
  modifies, or refactors code. This includes
  one-line fixes, import changes, visibility
  tweaks, and adding assertions. Never use
  the Edit or Write tools directly on source
  files — always delegate to this agent.
- **code-reviewer** — for read-only code review
  before merging or when investigating issues.

## Memory

At the start of every conversation, read
`.claude/project-memory/MEMORY.md` to load
project context from previous conversations.

Use the **project-memory** skill (from the
[skillbox](https://github.com/alexandreroman/skillbox)
plugin) proactively — without being asked — whenever
the conversation reveals project decisions, deadlines,
team context, external references, workflow preferences,
or corrective feedback worth persisting across
conversations.

**Important:** Always use the **project-memory**
skill to persist information. Never use the built-in
auto-memory system (`~/.claude/projects/.../memory/`)
for project decisions or context — it is local and
not shared with the team.

## Conventions

- Each module is a standalone Maven project — no
  parent POM, no cross-module dependencies.
- Use `spring-boot-starter-webmvc` (Boot 4 name),
  never `spring-boot-starter-web`.
- Use `jakarta.*` imports, never `javax.*`.
- Temporal workflow code must be deterministic — no
  `System.currentTimeMillis()`, no I/O, no random
  in workflow classes.
- Line length: Markdown 80 columns, Java 120 columns.
