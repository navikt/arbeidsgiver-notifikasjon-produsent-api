# AGENTS.md — GitHub Copilot Agent Mode Instructions

This file provides instructions for AI coding agents (GitHub Copilot Agent Mode in IntelliJ IDEA and VS Code) working in this monorepo.

## Repository Map

```
arbeidsgiver-notifikasjon-produsent-api/
├── app/                          # Kotlin backend (all microservices)
│   ├── pom.xml                   # Maven build (Kotlin 2.1, JVM 21)
│   ├── Dockerfile
│   ├── nais/                     # NAIS deployment manifests (dev + prod)
│   └── src/main/
│       ├── kotlin/no/nav/arbeidsgiver/notifikasjon/
│       │   ├── Main.kt                   # Entry point — dispatches to services
│       │   ├── hendelse/HendelseModel.kt # Sealed class: all event types
│       │   ├── produsent/api/            # Producer GraphQL mutations & queries
│       │   ├── bruker/                   # User-facing API
│       │   ├── ekstern_varsling/         # External notification (SMS/email)
│       │   ├── kafka_reaper/             # Event deletion (tombstoning)
│       │   ├── kafka_backup/             # Raw event backup
│       │   ├── skedulert_harddelete/     # Scheduled hard deletes
│       │   ├── skedulert_utgaatt/        # Scheduled expiration
│       │   ├── skedulert_paaminnelse/    # Scheduled reminders
│       │   ├── dataprodukt/              # BigQuery analytics export
│       │   └── infrastruktur/            # Shared infra (HTTP, Kafka, DB, auth)
│       └── resources/
│           ├── produsent.graphql          # Producer API schema (primary)
│           └── bruker.graphql             # User API schema
├── test-produsent/               # React test UI (TypeScript, Vite, Apollo)
├── fake-produsent-api/           # Mock GraphQL server (Node.js, Express)
├── widget/                       # NPM package @navikt/arbeidsgiver-notifikasjon-widget
├── terraform/                    # GCP infrastructure (BigQuery, service accounts)
├── docs/                         # User documentation (GitHub Pages)
├── adr/                          # Architecture Decision Records
├── docker-compose.yml            # Local dev: Kafka + PostgreSQL
└── local-db-init.sql             # Creates databases for all services
```

## Build & Test Commands

### Kotlin backend (app/)

```bash
# Build
cd app && mvn package -DskipTests

# Run tests (requires Docker for PostgreSQL and Kafka)
cd app && mvn test

# Run locally (after docker-compose up)
# Use LocalMain.kt run configuration in IntelliJ IDEA
```

### TypeScript test UI (test-produsent/)

```bash
cd test-produsent && npm ci && npm run build
```

### Widget (widget/)

```bash
cd widget/component && npm ci && npm test && npm run build
```

### Local environment

```bash
docker-compose up  # Starts Kafka (9092) and PostgreSQL (1337)
```

## Agent Task Guidelines

### When modifying Kotlin code

1. **Follow Kotlin Official code style** (ktfmt). IntelliJ formats on save.
2. **Sealed class exhaustiveness**: When adding a new event type to `HendelseModel.kt`, you must handle it in every `when` expression across all consumers. Search for `is HendelseModel.` to find all pattern matches.
3. **GraphQL schema-first**: Modify the `.graphql` schema file first, then implement the resolver in Kotlin. Mutations go in `produsent/api/Mutation*.kt`, queries in `Query*.kt`.
4. **Database migrations**: Add Flyway migration files in the appropriate service's resources directory. Migrations run automatically on startup.
5. **Test with embedded services**: Use `PostgresTestListener` and `LocalKafka` in tests — don't mock the database or Kafka.
6. **Coroutines**: The codebase uses `kotlinx-coroutines`. Use `suspend` functions and structured concurrency.

### When modifying TypeScript/React code

1. **Format with Prettier** (config in `.prettierrc`): 4-space indent, single quotes, semicolons.
2. **Lint with ESLint**: Run `npm run lint` before committing.
3. **GraphQL types are generated**: Run `npm run codegen` after changing `.graphql` files. Don't hand-write GraphQL types.
4. **Use NAV Design System components** (`@navikt/ds-react`) for UI.

### When modifying infrastructure

1. **NAIS manifests**: Dev configs in `app/nais/dev-gcp-*.yaml`, prod in `prod-gcp-*.yaml`.
2. **Terraform**: Run `terraform plan` before applying. Separate tfvars for dev/prod.
3. **GitHub Actions**: Workflows in `.github/workflows/`. The main build uses matrix strategy for all services.

## Critical Domain Knowledge

### Event Sourcing Architecture

- All state mutations produce events to the Kafka topic `fager.notifikasjon`
- Each microservice consumes the full event stream and builds its own PostgreSQL database
- The topic uses **log compaction** (not time-based retention) — events persist indefinitely until compacted
- Hard deletes write tombstone records to Kafka — this is **irreversible**

### Event Model (HendelseModel.kt)

The `HendelseModel` sealed class is the source of truth for all event types. Key subtypes:

- `BeskjedOpprettet`, `OppgaveOpprettet`, `KalenderavtaleOpprettet` — creation events
- `SakOpprettet`, `NyStatusSak`, `NesteStegSak` — case lifecycle
- `OppgaveUtført`, `OppgaveUtgått` — task completion
- `SoftDelete`, `HardDelete` — deletion events
- `EksterntVarselVellykket`, `EksterntVarselFeilet` — notification delivery status
- `PåminnelseOpprettet` — reminder events

**Warning**: Adding or changing event types affects ALL downstream services. The event schema is the contract between services.

### GraphQL API Design Patterns

- Mutations return a union type of success/error results (e.g., `NyBeskjedResultat`)
- All mutations require authentication and authorization (Altinn service codes or resource IDs)
- Use `MerkelappInput` with `lenke`, `tekst`, and `eksternId` for notification content
- `grupperingsid` ties notifications to a parent `Sak` (case)

### Norwegian Domain Terms

| Norwegian | English | Usage |
|---|---|---|
| Arbeidsgiver | Employer | Primary user group |
| Notifikasjon | Notification | Core domain concept |
| Beskjed | Message | Informational notification type |
| Oppgave | Task | Actionable notification type |
| Kalenderavtale | Calendar appointment | Time-bound notification type |
| Sak | Case | Groups related notifications |
| Hendelse | Event | Event sourcing event |
| Mottaker | Recipient | Notification target |
| Produsent | Producer | System that creates notifications |
| Bruker | User | End user (employer) |
| Varsling | Notification/Alert | External notification (SMS/email) |
| Påminnelse | Reminder | Scheduled reminder |
| Utgått | Expired | Deadline passed |
| Harddelete | Hard delete | Permanent deletion |
| Frist | Deadline | Task deadline |

### Access Control

- Producer API: Authenticated via Maskinporten tokens with organization number
- User API: Authenticated via TokenX with person identity
- Authorization: Checked against Altinn service codes or resource IDs per organization
- Inbound access policy defined in NAIS manifests (allowlisted applications)

## Code Review Checklist

When proposing changes, verify:

- [ ] New event types in `HendelseModel.kt` are handled in all `when` expressions
- [ ] GraphQL schema changes are reflected in both schema files and Kotlin resolvers
- [ ] Database migrations are backward-compatible (no destructive changes without coordination)
- [ ] NAIS manifests are updated for both dev and prod if adding new env vars or resources
- [ ] Tests pass: `mvn test` for Kotlin, `npm test` for widget
- [ ] No secrets or credentials in committed code
- [ ] Norwegian naming conventions are consistent with existing code
