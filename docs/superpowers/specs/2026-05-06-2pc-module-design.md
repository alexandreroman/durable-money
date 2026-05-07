# Module `2pc` — Design

**Date** : 2026-05-06
**Branche** : `feature/2pc`
**Auteur** : design validé en collaboration avec l'utilisateur via brainstorming

## Résumé

Ajouter un nouveau module `2pc` au tutoriel `durable-money`, inséré
entre `2-microservices` et `3-messaging`. Le module démontre le
protocole de validation à deux phases (Two-Phase Commit) appliqué à
deux microservices, en exploitant les transactions préparées natives
de PostgreSQL (`PREPARE TRANSACTION` / `COMMIT PREPARED` /
`ROLLBACK PREPARED`).

Pas de gestionnaire JTA. Pas de Narayana. L'application implémente
le protocole 2PC à la main au niveau SQL. C'est la version la plus
transparente sur le plan pédagogique : les étudiants voient les
phases du protocole se dérouler comme des appels REST + SQL
explicites, et peuvent inspecter `pg_prepared_xacts` pendant la
fenêtre prepare → commit.

## Narration pédagogique

> Module 2 a cassé l'ACID en répartissant l'état sur deux services.
> Module 2pc le restaure avec la solution classique : un protocole
> de validation à deux phases. Le coordinateur demande à chaque
> participant *« peux-tu valider ? »* (PREPARE), attend tous les
> YES, persiste sa décision, puis ordonne le COMMIT PREPARED à
> tous. On obtient l'atomicité entre les deux services. Mais on
> découvre les faiblesses du protocole : c'est synchrone bloquant,
> le coordinateur devient un point central, les transactions
> in-doubt occupent des verrous tant que la décision n'arrive pas.
> Cela motivera le pivot vers l'asynchrone (module 3) puis la saga
> (module 4).

## Positionnement dans la progression

Le module est inséré entre `2-microservices` et `3-messaging`. Le
nom du dossier est **`2pc`** (sans préfixe numérique) pour
préserver l'ordre des modules numérotés existants sans casser leur
numérotation.

| Module            | Approche               | Concept clé                                  |
| ----------------- | ---------------------- | -------------------------------------------- |
| `1-monolith`      | Monolithe + ACID       | Un `@Transactional` couvre tout              |
| `2-microservices` | REST microservices     | Calls distribués sans filet                  |
| `2pc`             | 2PC + Postgres prepared tx | 2-phase commit piloté à la main au SQL    |
| `3-messaging`     | RabbitMQ + DLQ         | Résilience asynchrone, pas de compensation   |
| `4-temporal`      | Temporal + Saga        | Exécution durable avec compensation auto     |

## Architecture

```mermaid
graph TD
    Client --> Transfer[transfer-service :8080]
    Transfer -->|/debit/prepare| Account[account-service :9080]
    Transfer -->|/credit/prepare| Account
    Transfer -->|PREPARE TRANSACTION journal| Postgres[(PostgreSQL)]
    Transfer -->|INSERT transfer_decisions| Postgres
    Transfer -->|/xa/{xid}/commit ou rollback| Account
    Transfer -->|COMMIT/ROLLBACK PREPARED journal| Postgres
    Account --> Postgres
```

- 2 microservices Java/Spring Boot 4.0.5 — `transfer-service` et
  `account-service` — calques structurels des modules 2 et 3
- 1 instance PostgreSQL 17 partagée, 2 schémas distincts pour
  isoler logiquement les deux services
- Le `transfer-service` est à la fois **coordinateur** du 2PC et
  **participant** (pour son journal local)
- Le `account-service` est **participant** sur deux opérations
  distinctes (debit + credit)
- Au total **3 participants** dans une transaction globale

## Modèle de données

### Configuration PostgreSQL

`max_prepared_transactions = 50` est requis. Activé via le
`compose.yaml` :

```yaml
postgres:
  image: postgres:17-alpine
  command: postgres -c max_prepared_transactions=50
  environment:
    POSTGRES_DB: moneydb
    POSTGRES_USER: demo
    POSTGRES_PASSWORD: demo
```

### Schéma `account` (utilisé par account-service)

```sql
CREATE SCHEMA IF NOT EXISTS account AUTHORIZATION demo;

CREATE TABLE account.accounts (
    id          UUID PRIMARY KEY,
    owner       TEXT NOT NULL,
    balance     NUMERIC(19, 4) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

Seed identique aux autres modules (Alice 1000.00, Bob 100.00, mêmes
UUID).

### Schéma `transfer` (utilisé par transfer-service)

```sql
CREATE SCHEMA IF NOT EXISTS transfer AUTHORIZATION demo;

CREATE TABLE transfer.transfers (
    id                  UUID PRIMARY KEY,
    source_account_id   UUID NOT NULL,
    target_account_id   UUID NOT NULL,
    amount              NUMERIC(19, 4) NOT NULL,
    status              TEXT NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL,
    completed_at        TIMESTAMPTZ
);

CREATE TABLE transfer.transfer_decisions (
    transfer_id   UUID PRIMARY KEY,
    decision      TEXT NOT NULL,
    participants  JSONB NOT NULL,
    decided_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

`transfer.transfers` est le journal métier. La ligne est insérée
**à l'intérieur** d'une transaction préparée (le journal est un
participant du 2PC).

`transfer.transfer_decisions` est la trace de durabilité du
coordinateur. Elle est écrite **hors** du 2PC, en transaction
locale autonome, juste avant la phase commit. Cette écriture est
le **point de non-retour** du protocole : si le coordinateur
survit jusqu'à elle, il sait quoi faire ; sinon le rollback est la
seule issue sûre. Stocke la décision (`COMMIT` / `ABORT`) et un
tableau JSON plat des `xid` des participants — par exemple
`["transfer-<uuid>-debit", "transfer-<uuid>-credit",
"transfer-<uuid>-journal"]` — pour permettre une réémission
idempotente des `COMMIT PREPARED` / `ROLLBACK PREPARED`. Le code
de recovery est explicitement hors scope de ce module ; un
schéma structuré du type `{xid, kind, endpoint}` serait
prématuré (YAGNI).

### Format des `xid`

Format déterministe : `transfer-<transferId>-<role>` avec
`<role>` ∈ `{debit, credit, journal}`. Avantages :
- déterministe — utile pour réémissions idempotentes et pour
  l'inspection humaine via `pg_prepared_xacts`
- bien sous la limite des 200 caractères imposée par
  `PREPARE TRANSACTION`

## Protocole 2PC — flux nominal

```
1. Client → POST transfer-service /transfers
2. transfer-service génère transferId (UUID v7) + 3 xids déterministes
3. Phase PREPARE (séquentielle pour la lisibilité)
   a. POST account-service /accounts/{src}/debit/prepare {amount, xid="...-debit"}
        → UPDATE account.accounts SET balance = balance - amount
            WHERE id = src AND balance >= amount
        → si rowcount = 0 : ROLLBACK + 409 (insufficient funds)
        → sinon : PREPARE TRANSACTION 'xid-debit' + 200
   b. POST account-service /accounts/{tgt}/credit/prepare → idem en crédit
   c. transfer-service local :
        INSERT INTO transfer.transfers (..., status='PREPARED')
        PREPARE TRANSACTION 'xid-journal'
4. Phase DECIDE (durabilité, point de non-retour)
   INSERT INTO transfer.transfer_decisions (transfer_id, decision='COMMIT',
       participants=["xid-debit", "xid-credit", "xid-journal"])
   en transaction locale autonome
5. Phase COMMIT (séquentielle, idempotente)
   POST account-service /xa/{xid-debit}/commit
   POST account-service /xa/{xid-credit}/commit
   COMMIT PREPARED 'xid-journal' en local
6. UPDATE transfer.transfers SET status='COMMITTED', completed_at=now()
   en transaction locale post-2PC
7. Réponse 200 OK + Transfer complet
```

## Protocole 2PC — flux rollback métier

### Insufficient funds détecté en phase prepare

```
3a. /debit/prepare → 409 (rowcount=0 dans le UPDATE conditionnel)
3b. /credit/prepare est sauté
3c. local journal prepare est sauté
4.  INSERT transfer_decisions (decision='ABORT', participants=[])
5.  Aucun PREPARE n'a réussi → rien à ROLLBACK PREPARED
6.  INSERT INTO transfer.transfers (status='ABORTED', ...) tx locale
7.  Réponse 409 Conflict + détail métier
```

### Échec après debit prepared

```
3a. /debit/prepare → 200 (xid-debit prepared)
3b. /credit/prepare → 409 (compte cible inexistant, etc.)
4.  INSERT transfer_decisions (decision='ABORT', participants=[xid-debit])
5.  POST /xa/{xid-debit}/rollback → ROLLBACK PREPARED → debit annulé
6.  INSERT INTO transfer.transfers (status='ABORTED', ...)
7.  Réponse 409
```

## Idempotence

Les endpoints `/xa/{xid}/commit` et `/xa/{xid}/rollback` doivent
tolérer un appel répété :

- Si `pg_prepared_xacts` ne contient plus le `xid`, on retourne
  200 sans erreur — la transaction a déjà été statuée
- Sinon on émet `COMMIT PREPARED` ou `ROLLBACK PREPARED`

Cette idempotence est nécessaire pour les retries réseau côté
coordinateur (mais le scope du tutoriel n'inclut pas de chaos
endpoints qui exerceraient cette logique).

## Scope explicite

✅ Inclus :
- Cas nominal : succès atomique
- Rollback métier (insufficient funds, account not found)
- Idempotence des endpoints commit/rollback
- Journal de décision durable (`transfer_decisions`)

❌ Exclus :
- Pas d'endpoints de chaos pour injection de crash
- Pas de recovery automatique au démarrage du coordinateur (le
  journal `transfer_decisions` est en place pour la **correction**
  du protocole, mais le code de rejeu n'est pas implémenté — c'est
  hors scope tutoriel et serait visible comme dette pédagogique
  dans la doc)
- Pas de tests automatisés (cohérent avec les autres modules)

## Layout Java

### Structure de répertoires

```
2pc/
├── compose.yaml
├── init-db/
│   ├── 01-schema.sql           # schémas + tables
│   └── 02-data.sql             # seed Alice + Bob
├── account-service/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/{java,resources}/
└── transfer-service/
    ├── Dockerfile
    ├── pom.xml
    └── src/main/{java,resources}/
```

### `account-service` — package `io.temporal.demos.durablemoney.twopc.account`

```
AccountServiceApplication.java
Account.java                       # record (id, owner, balance, createdAt)
AccountController.java             # POST /accounts, GET /accounts/{id}
   ├── nested record CreateAccountRequest
   └── nested record AccountResponse
PrepareController.java             # POST /accounts/{id}/{debit|credit}/prepare
   ├── nested record PrepareRequest(amount, xid)
   └── nested enum Operation { DEBIT, CREDIT }
XaController.java                  # POST /xa/{xid}/{commit|rollback}
AccountRepository.java             # JdbcClient wrapper
PreparedTransactionService.java    # logique métier + PREPARE TRANSACTION
   └── nested exception InsufficientFundsException
```

`PreparedTransactionService` :
- Utilise `JdbcClient` directement, **sans** `@Transactional` Spring.
  Le contrôle commit/prepare est manuel via SQL brut sur une
  connexion dédiée par requête.
- Séquence : `Connection.setAutoCommit(false)` →
  `UPDATE accounts ...` → `PREPARE TRANSACTION '<xid>'` → relâche
  la connexion. Validation stricte du `xid` par regex (le
  paramètre n'est pas bindable, c'est du SQL string-builder).
- `commit` / `rollback` : vérifie `pg_prepared_xacts` pour
  l'idempotence, puis `COMMIT PREPARED '<xid>'` ou
  `ROLLBACK PREPARED '<xid>'`.

### `transfer-service` — package `io.temporal.demos.durablemoney.twopc.transfer`

```
TransferServiceApplication.java
Transfer.java                      # record domain
TransferController.java            # POST /transfers, GET /transfers/{id}
   ├── nested record TransferRequest
   └── nested record TransferResponse
TransferRepository.java            # JdbcClient
TransferDecisionRepository.java    # transfer_decisions
TransferCoordinator.java           # orchestre les 3 phases
   ├── nested record Participant(xid, endpointBase, kind)
   └── nested enum Decision { COMMIT, ABORT }
AccountServiceClient.java          # RestClient — prepare/commit/rollback
   └── nested record PrepareCommand
```

`TransferCoordinator` (squelette) :

```java
public TransferResult execute(TransferRequest req) {
    var transferId = UuidCreator.getTimeOrderedEpoch();
    var participants = List.of(
        new Participant(xidFor(transferId, "debit"),  accountUrl, DEBIT),
        new Participant(xidFor(transferId, "credit"), accountUrl, CREDIT),
        new Participant(xidFor(transferId, "journal"), null,      JOURNAL)
    );

    var prepareOutcome = preparePhase(transferId, req, participants);

    var decision = prepareOutcome.allYes() ? COMMIT : ABORT;
    decisionRepo.record(transferId, decision, participants);

    finalizePhase(transferId, decision, prepareOutcome.preparedXids());
    return finalize(transferId, decision, prepareOutcome.businessError());
}
```

### Gestion des exceptions

`@ExceptionHandler` localisés par contrôleur (mémoire projet :
pas de `@RestControllerAdvice` global). Codes HTTP :
- `409 Conflict` : insufficient funds, account not found
- `502 Bad Gateway` : panne réseau participant en phase prepare
  avant qu'aucun `xid` n'ait été prepared
- `500 Internal Server Error` : bug

## Configuration Spring Boot

### `application.yaml` — account-service

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:5432/moneydb?currentSchema=account
    username: ${DB_USER:demo}
    password: ${DB_PASS:demo}
server:
  port: 9080
```

### `application.yaml` — transfer-service

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:5432/moneydb?currentSchema=transfer
    username: ${DB_USER:demo}
    password: ${DB_PASS:demo}
account-service:
  url: ${ACCOUNT_SERVICE_URL:http://localhost:9080}
server:
  port: 8080
```

### Conventions héritées (mémoire projet)

- `application-dev.yaml` présent dans chaque service même si vide
- `spring-boot-devtools` runtime+optional dans tous les `pom.xml`
- Pas de CDS dans les Dockerfiles
- `spring-boot-starter-webmvc` (Boot 4), pas `-web`
- Imports `jakarta.*` uniquement
- `OffsetDateTime` au binding JdbcClient (Postgres ne sait pas
  inférer le type SQL d'un `Instant`), conversion via
  `.atOffset(ZoneOffset.UTC)` au site de binding
- Records et enums imbriqués dans la classe propriétaire (sauf
  partage entre plusieurs)
- Référencer les types nichés par leur nom simple (import)

## Dépendances Maven (par service)

- `spring-boot-starter-webmvc`
- `spring-boot-starter-jdbc`
- `spring-boot-http-client` *(transfer-service uniquement)*
- `spring-boot-starter-validation`
- `spring-boot-starter-actuator`
- `spring-boot-devtools` (runtime, optional)
- `org.postgresql:postgresql`
- `com.github.f4b6a3:uuid-creator` (UUIDv7)

## API publique

### Identique aux autres modules

```bash
POST /transfers
{
  "sourceAccountId": "...",
  "targetAccountId": "...",
  "amount": 200.00
}
```

**Réponse pour ce module** : **`200 OK` + Transfer complet**
(comme module 1) — synchrone, atomique, le 2PC restaure les
propriétés du monolithe.

| Module | Status       | Réponse                                              |
| ------ | ------------ | ---------------------------------------------------- |
| 1      | 200 OK       | full Transfer                                        |
| 2      | 200 OK       | `{transferId, status, message}` (peut perdre l'argent) |
| 2pc    | 200 OK       | full Transfer (atomique via 2PC)                     |
| 3      | 202 Accepted | `{id, status, message, ...}` async                   |
| 4      | 202 Accepted | `{transferId}` Temporal                              |

## Tutorial flow

```bash
cd 2pc
podman compose up --build

# Transfert nominal
curl -X POST http://localhost:8080/transfers \
  -H 'Content-Type: application/json' \
  -d '{"sourceAccountId":"91a12083-e27d-48b8-b67a-b28a8207db8d",
       "targetAccountId":"d2ff0ba8-79c4-4ea7-b297-26847d553d63",
       "amount":200.00}'

# Rollback métier
curl -X POST http://localhost:8080/transfers \
  -d '{"sourceAccountId":"d2ff0ba8-...","targetAccountId":"91a12083-...",
       "amount":99999.00}'

# Inspecter les transactions in-doubt pendant un transfert
podman exec -it 2pc_postgres psql -U demo -d moneydb \
  -c "SELECT * FROM pg_prepared_xacts;"
```

## Mises à jour de la doc racine

Le `README.md` racine doit être mis à jour :

1. Tableau **Modules** : ajouter ligne pour `2pc`
2. Tableau **Transfer responses across modules** : ajouter ligne
   2pc avec status 200 + full Transfer
3. Section **Architecture** : ajouter sous-section `Module 2pc —
   Two-phase commit (PostgreSQL prepared transactions)` avec le
   diagramme Mermaid
4. Section **Configuration** : noter que ce module nécessite
   `max_prepared_transactions=50` côté Postgres

## Risques & non-décidés

- **`PREPARE TRANSACTION` n'accepte pas de paramètres bindés**.
  L'`xid` doit être inséré par concaténation. Mitigation :
  validation stricte du `xid` par regex
  `^transfer-[0-9a-f-]+-(debit|credit|journal)$` côté
  account-service avant d'émettre la commande SQL.
- **Pas de recovery automatique** : si le coordinateur crashe
  entre l'écriture de `transfer_decisions` et l'émission des
  `COMMIT PREPARED`, des transactions in-doubt resteront sur
  account-service jusqu'à intervention manuelle (vu via
  `pg_prepared_xacts`). C'est un choix tutoriel — le coût
  pédagogique du recovery serait disproportionné. La doc le
  mentionnera.
- **Verrouillage pendant la fenêtre prepare → commit** : les
  lignes mises à jour sont verrouillées tant que la transaction
  n'est pas validée. Pour un transfert simultané sur les mêmes
  comptes, il y aura attente — c'est précisément la pathologie
  que le tutoriel veut illustrer pour motiver les modules
  suivants.

## Prochaine étape

Une fois ce design validé, la phase d'implémentation passe par
l'agent `code-writer` (convention CLAUDE.md du projet : aucun
code n'est écrit avec Edit/Write directement, tout passe par cet
agent).
