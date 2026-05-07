# 4 — Messaging (RabbitMQ + DLQ)

The same money transfer, this time decoupled with
RabbitMQ. The transfer-service publishes commands; the
account-service consumes them and publishes results back.
Each service drains its DLQ into a `dlq_messages` table
and exposes an `/admin/dlq` REST surface so operators
can inspect, replay, or discard parked messages.

The architecture trades the synchronous coordinator of
module 3 for asynchronous resilience — but it still has
no automatic compensation, so a failed credit after a
successful debit still leaves money lost (just visibly,
in the DLQ).

## What's inside

```
4-messaging/
├── compose.yaml              # PostgreSQL + RabbitMQ + the two services
├── init-db/init.sql          # Creates "account" and "transfer" schemas
├── account-service/          # Command consumer, port 9080
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/
│       ├── java/.../account/
│       │   ├── AccountController.java         # CRUD only (no debit/credit HTTP)
│       │   ├── AccountCommandListener.java    # @RabbitListener on account.commands
│       │   ├── DlqMessageListener.java        # drains account.commands.dlq
│       │   ├── DlqAdminController.java        # /admin/dlq REST surface
│       │   ├── RabbitConfig.java              # exchanges, queues, bindings, DLQ
│       │   └── ...
│       └── resources/
│           ├── application.yaml
│           ├── schema.sql                     # accounts + dlq_messages tables
│           └── data.sql                       # seed Alice + Bob
└── transfer-service/         # Initiator + result handler, port 8080
    ├── Dockerfile
    ├── pom.xml
    └── src/main/
        ├── java/.../transfer/
        │   ├── TransferController.java
        │   ├── TransferService.java           # initiates, persists, publishes
        │   ├── TransferResultListener.java    # @RabbitListener on transfer.results
        │   ├── DlqMessageListener.java        # drains transfer.results.dlq
        │   ├── DlqAdminController.java        # /admin/dlq REST surface
        │   ├── RabbitConfig.java              # exchanges, queues, bindings, DLQ
        │   └── ...
        └── resources/
            ├── application.yaml
            └── schema.sql                     # transfers + dlq_messages tables
```

Two RabbitMQ direct exchanges:

- `money.exchange` — commands and results.
- `money.dlx` — dead-letter exchange.

Two functional queues, each with a dedicated DLQ:

- `account.commands` → `account.commands.dlq`
- `transfer.results` → `transfer.results.dlq`

The `transfers` table tracks state with a four-value
`status` column: `DEBITING` → `CREDITING` →
`COMPLETED`, or `… → FAILED`.

## Pedagogical goals

- Show that asynchronous messaging removes the *coupled
  availability* of synchronous REST or 2PC: the
  transfer-service can keep accepting transfers even
  when the account-service is briefly unreachable.
- Introduce the DLQ as the standard pattern for
  "messages we cannot process": instead of crashing the
  consumer or losing work, the broker parks the failure
  for inspection.
- Show the operational cost of *managing* a DLQ:
  draining it into durable storage, exposing an admin
  surface to inspect, replay, and discard parked
  messages, and relying on consumer-side idempotency so
  replays do not double-debit. None of this is free.
- Demonstrate the *limits* of plain pub/sub for money:
  the DLQ catches infrastructure failures, but a
  business failure of the credit still loses money
  unless the application implements a compensating
  action.
- Set up module 5: this is exactly the gap a Saga fills.

## Architecture

```mermaid
graph TD
    Client --> Transfer[transfer-service :8080]
    Transfer -->|"persist DEBITING<br/>publish DebitCommand"| MQ["RabbitMQ :5672<br/>money.exchange"]
    MQ -->|account.commands| Account[account-service :9080]
    Account -->|"AccountResult<br/>transfer.results"| MQ
    MQ --> Transfer
    Transfer -->|"persist CREDITING<br/>publish CreditCommand"| MQ
    MQ -.->|"message rejected"| DLX["money.dlx<br/>account.commands.dlq<br/>transfer.results.dlq"]
    DLX -.->|"drain"| Drain["dlq_messages tables<br/>(account + transfer schemas)"]
    Operator["operator<br/>/admin/dlq"] -.->|"list / replay / discard"| Drain
    Drain -.->|"replay republishes<br/>to money.exchange"| MQ
```

Per-transfer flow:

1. `POST /transfers` → insert with status `DEBITING` and
   publish `DebitCommand` to `account.commands`.
2. account-service consumes the command, applies the
   debit, publishes a success/failure result to
   `transfer.results`.
3. transfer-service consumes the result. On debit
   success, it transitions to `CREDITING` and publishes
   `CreditCommand`. On debit failure, it transitions to
   `FAILED`.
4. On credit success: `COMPLETED`. On credit failure:
   `FAILED` — and the source remains debited (see
   limitations below).

DLQ flow (when a consumer rejects a message):

1. RabbitMQ dead-letters the rejected message to
   `money.dlx` with the `x-death` header set, and the
   broker delivers it to `account.commands.dlq` or
   `transfer.results.dlq` depending on the source queue.
2. The matching service's `DlqMessageListener` pulls the
   message, parses the JSON body to extract `transferId`,
   reads the `x-death` reason and count, and inserts a
   row in `dlq_messages` with status `PARKED`. The
   broker queue is now empty; the database row is the
   durable record.
3. An operator queries `GET /admin/dlq` to inspect what
   is parked, then either:
   - calls `POST /admin/dlq/{id}/replay` — the original
     payload bytes are re-published to `money.exchange`
     under the original routing key, the row flips to
     `REPLAYED`, and the regular listener processes it
     again; or
   - calls `DELETE /admin/dlq/{id}` to mark it
     `DISCARDED` and stop tracking.

Replay relies on the existing consumer-side idempotency
in `AccountService.debit/credit` (a unique
`(transfer_id, operation)` slot in the `transfers`
table). A redelivered debit or credit for the same
transfer never moves balance twice.

## Build and run

### With Docker Compose

```bash
cd 4-messaging
docker compose up --build
```

Starts PostgreSQL on `5432`, RabbitMQ on `5672` (with the
management UI on `15672`, `guest`/`guest`),
account-service on `9080`, transfer-service on `8080`.

> **Note for Podman users:** the `compose.yaml` sets
> `user: rabbitmq` on the `rabbitmq:4-management-alpine`
> image so the entrypoint skips the root `chown` + `gosu`
> branch under rootless Podman.

### Local Maven build

```bash
cd 4-messaging/account-service
mvn package -DskipTests
java -jar target/*.jar

# In another terminal
cd 4-messaging/transfer-service
mvn package -DskipTests
java -jar target/*.jar
```

You will need a local PostgreSQL on `localhost:5432` and
a local RabbitMQ on `localhost:5672`. Override
`DB_HOST`, `RABBITMQ_HOST`, etc. as needed.

Or build everything from the repo root:

```bash
task build
```

## API

`transfer-service` (port `8080`):

| Method | Path                | Behavior                                                                |
| ------ | ------------------- | ----------------------------------------------------------------------- |
| POST   | `/transfers`        | Async; `202 Accepted` with `{id, status, message, createdAt, updatedAt}` |
| GET    | `/transfers/{id}`   | Poll for current status (`DEBITING`/`CREDITING`/`COMPLETED`/`FAILED`)   |

`account-service` (port `9080`):

| Method | Path             | Behavior                          |
| ------ | ---------------- | --------------------------------- |
| POST   | `/accounts`      | Create an account                 |
| GET    | `/accounts`      | List all accounts                 |
| GET    | `/accounts/{id}` | Get one account                   |

Note that account-service exposes **no** debit/credit
HTTP endpoints in this module — those operations are
driven exclusively through the RabbitMQ command queue.

DLQ admin (both services, same shape):

| Method | Path                       | Behavior                                                                                          |
| ------ | -------------------------- | ------------------------------------------------------------------------------------------------- |
| GET    | `/admin/dlq`               | List parked messages. Optional `?status=PARKED\|REPLAYED\|DISCARDED` filter; default returns all. |
| GET    | `/admin/dlq/{id}`          | Get one parked message including original payload                                                 |
| POST   | `/admin/dlq/{id}/replay`   | Re-publish to original exchange/routing key; `409 Conflict` if not `PARKED`                       |
| DELETE | `/admin/dlq/{id}`          | Mark as `DISCARDED`; `409 Conflict` if already resolved                                           |

The shape is identical across services; only the queue
each service drains differs (`account.commands.dlq` vs
`transfer.results.dlq`).

```bash
# Initiate a transfer (returns 202 immediately)
curl -i -X POST http://localhost:8080/transfers \
  -H "Content-Type: application/json" \
  -d '{
    "sourceAccountId": "91a12083-e27d-48b8-b67a-b28a8207db8d",
    "targetAccountId": "d2ff0ba8-79c4-4ea7-b297-26847d553d63",
    "amount": 200.00
  }'

# Poll until COMPLETED or FAILED
curl -s http://localhost:8080/transfers/<id> | jq .

# List currently parked messages on the account-service DLQ
curl -s http://localhost:9080/admin/dlq?status=PARKED | jq .

# Replay one
curl -s -X POST http://localhost:9080/admin/dlq/<id>/replay | jq .

# Or discard it
curl -s -X DELETE http://localhost:9080/admin/dlq/<id> | jq .
```

## Failure modes and limitations

- **No automatic compensation on credit failure.** If the
  credit message is processed and rejected (insufficient
  funds at the target, validation error, persistent 5xx),
  the transfer is marked `FAILED` while the source
  account stays debited. There is no `reverseDebit`
  message published. Money is still lost in the system —
  the DLQ just makes the event observable.
- **Replay is manual.** The `/admin/dlq` endpoints make
  parked messages observable and re-deliverable, but
  nothing decides *when* a message should be replayed,
  how many attempts it gets, or what backoff to apply.
  An operator (or an external scheduler) drives every
  retry. A real recovery policy — exponential backoff,
  maximum-attempt budgets, dead-after-N-tries — is not
  in scope.
- **Replay is at-least-once.** Re-publishing the message
  bytes goes through the same listener, which already
  guards against duplicate balance moves via the
  `(transfer_id, operation)` slot in the `transfers`
  table. Without that guard, every replay would
  re-debit or re-credit. Module 5 reuses the same
  idempotency contract for Temporal activities.
- **Dual-write window.** The transfer-service updates the
  `transfers` table *and* publishes a Rabbit message in
  separate operations. A crash in between can leave the
  row in `CREDITING` with no credit command in flight.
  An outbox pattern would close this gap; the tutorial
  accepts it for clarity.
- **Eventual visibility.** Clients no longer see a
  synchronous outcome. They must poll
  `GET /transfers/{id}` until the status reaches a
  terminal state.

Module 5 closes the compensation gap with a Temporal
Saga: a workflow durably owns the transfer state, retries
activities automatically, and runs a `reverseDebit`
compensation if the credit ultimately fails.
