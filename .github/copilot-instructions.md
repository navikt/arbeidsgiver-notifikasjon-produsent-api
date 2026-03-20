# GitHub Copilot Instructions

## Project Overview

This is the **arbeidsgiver-notifikasjon-produsent-api** monorepo — a notification platform for employers ("arbeidsgivere") built on an Event Sourcing architecture. The central Kafka topic `fager.notifikasjon` is the event backbone, and multiple microservices consume from it to build their own materialized views in separate PostgreSQL databases.

The platform is owned by **team fager** in NAV (Norwegian Labour and Welfare Administration) and runs on the NAIS platform (Kubernetes on GCP).

## Monorepo Structure

| Module | Stack | Purpose |
|---|---|---|
| `app/` | Kotlin, Maven, Ktor 3, GraphQL | Main backend — all microservices in one build |
| `test-produsent/` | TypeScript, React 19, Vite, Apollo Client | Interactive test UI for the producer API |
| `fake-produsent-api/` | Node.js, Express, Apollo Server | Mock GraphQL server for testing |
| `widget/` | TypeScript, React, Vite, NPM package | Published component `@navikt/arbeidsgiver-notifikasjon-widget` |
| `terraform/` | HCL | BigQuery datasets, service accounts, GCP infra |
| `docs/` | Markdown | User-facing documentation (GitHub Pages) |
| `adr/` | Markdown | Architecture Decision Records |

## Language & Framework Guidelines

### Kotlin (app/)

- **Kotlin 2.1+** targeting **JVM 21**
- **Ktor 3** with CIO engine for HTTP servers
- **graphql-java** for GraphQL schema-first API (schemas in `src/main/resources/*.graphql`)
- **Jackson** with `JavaTimeModule` for JSON serialization
- **kotlinx-coroutines** for async operations
- **Kafka Clients 3.9** for event production/consumption
- **PostgreSQL** with HikariCP connection pooling and **Flyway** migrations
- **Micrometer + Prometheus** for metrics
- **Apache CXF** for Altinn SOAP/WSDL integration
- **Code style**: Kotlin Official (ktfmt). Use `when` expressions, sealed classes, data classes, and extension functions idiomatically.
- **Package root**: `no.nav.arbeidsgiver.notifikasjon`

### TypeScript / React (test-produsent/, widget/)

- **TypeScript 5.9+**, **React 19**, **Vite 7**
- **Apollo Client** for GraphQL
- **NAV Design System** (`@navikt/ds-react`, `@navikt/ds-css`)
- **GraphQL Codegen** for type generation from `.graphql` schemas
- **Prettier**: 4 spaces, single quotes, trailing commas (es5), semicolons, 100 char width
- **ESLint** with `@typescript-eslint` and React hooks rules
- **Vitest** for testing with Testing Library

## Architecture Concepts

### Event Sourcing

All state changes are modeled as events (hendelser) published to the Kafka topic. Each microservice builds its own read model from these events. The `HendelseModel.kt` sealed class hierarchy defines all event types.

Core notification types (union `Notifikasjon`):
- **Beskjed** — informational message
- **Oppgave** — task with optional deadline and reminder
- **Kalenderavtale** — calendar appointment

Recipient types (mottakere):
- `AltinnMottaker` — service code based (legacy Altinn)
- `AltinnRessursMottaker` — resource ID based (new Altinn)
- `NaermesteLederMottaker` — nearest manager relation

### Microservices (all in app/)

All services are defined in `Main.kt` and share a single Maven build. Each service runs as its own container in production, dispatched by an environment variable or command-line argument.

Key services: `produsent-api`, `bruker-api`, `bruker-api-writer`, `kafka-reaper`, `ekstern-varsling`, `skedulert-utgaatt`, `skedulert-paaminnelse`, `skedulert-harddelete`, `kafka-backup`, `dataprodukt`, `hendelse-transformer`, `replay-validator`.

### GraphQL APIs

Two schema-first GraphQL APIs:
- **Produsent API** (`produsent.graphql`) — for producing systems to create notifications, tasks, calendar events, and cases
- **Bruker API** (`bruker.graphql`) — for end-user-facing applications to read notifications

Custom scalars: `ISO8601DateTime`, `ISO8601LocalDateTime`, `ISO8601Duration`, `ISO8601Date`.

### Database Pattern

Each microservice has its own PostgreSQL database (CQRS). Migrations are managed with Flyway and auto-run on startup. Database names follow the pattern `<service>-model` (e.g., `produsent-model`, `bruker-model`).

## Coding Conventions

### Kotlin

- GraphQL mutations live in `produsent/api/Mutation*.kt` files (one file per mutation)
- GraphQL queries live in `produsent/api/Query*.kt` files
- Infrastructure code (HTTP, Kafka, DB, auth) is in `infrastruktur/`
- Use sealed classes and `when` expressions for exhaustive type matching
- Prefer immutable data structures (`val`, `data class`)
- Test with JUnit 5, use `PostgresTestListener` for DB tests and `LocalKafka` for Kafka tests
- Norwegian naming is acceptable and common in domain types (e.g., `Beskjed`, `Oppgave`, `Mottaker`, `Hendelse`)

### TypeScript

- Use functional components with hooks
- Use Apollo Client hooks (`useQuery`, `useMutation`) for GraphQL
- Generate types from GraphQL schemas — don't hand-write GraphQL types

### Infrastructure

- NAIS application manifests in `app/nais/` follow the pattern `{env}-{service}.yaml`
- Health endpoints: `/internal/alive`, `/internal/ready`, `/internal/metrics`
- All services use structured JSON logging (logstash-logback-encoder)
- Observability: Prometheus metrics, Loki logging, Java auto-instrumentation

## Testing

- **Kotlin**: `mvn test` runs JUnit 5 tests with embedded PostgreSQL and Kafka
- **Widget**: `npm test` runs Vitest
- **CI**: GitHub Actions workflows in `.github/workflows/`

## Local Development

```bash
# Start dependencies
docker-compose up   # Kafka (port 9092), PostgreSQL (port 1337)

# Run the app
# Use LocalMain.kt in IntelliJ IDEA

# GraphQL IDE
# http://ag-notifikasjon-produsent-api.localhost:8081/api/ide
# http://ag-notifikasjon-bruker-api.localhost:8081/api/ide
```

## Important Warnings

- **Do not** rely on Kafka metadata timestamps for event creation time — use explicit fields (see ADR #2)
- **Do not** change the Kafka topic cleanup policy or partition count without team coordination
- **Do not** modify shared event types in `HendelseModel.kt` without considering all downstream consumers
- **Be careful** with hard deletes — they tombstone events in Kafka and are irreversible
- The `fager.notifikasjon` topic uses log compaction, not time-based retention
