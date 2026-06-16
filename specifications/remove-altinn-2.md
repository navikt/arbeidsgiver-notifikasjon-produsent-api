# Specification: Remove Altinn 2 Support

## Background

Altinn 2 APIs are being sunset in June 2026. This project currently uses Altinn 2 for:
1. **External notifications (varsler)** via SOAP — sending SMS, email, and Altinn service notifications through the Altinn 2 NotificationAgencyExternalBasic API
2. **Access control (mottaker)** — using Altinn 2 serviceCode/serviceEdition to determine who can see notifications

This specification covers the complete removal of Altinn 2 **sending** capabilities and the deprecation of Altinn 2 **input types** in the producer (produsent) GraphQL API.

## Guiding Principles

- **Event model is unchanged.** The `AltinntjenesteVarselKontaktinfo` and `AltinnMottaker` types in `Hendelse.kt` are part of the event source (Kafka). Historical events must remain deserializable. Do not modify `Hendelse.kt` event model types.
- **Database migrations are append-only.** Do not remove or alter existing Flyway migrations. The repository code that *reads* Altinn 2 data from the database must remain functional for historical data.
- **Deprecate before remove in GraphQL.** Mark Altinn 2 input types as `@deprecated` and reject them at runtime with `UgyldigMottaker`, rather than removing them from the schema immediately. This gives consumers time to notice and adapt.

---

## Task 1: Deprecate Altinn 2 Input Types in GraphQL Schema

**File:** `app/src/main/resources/produsent.graphql`

### 1a. Deprecate `AltinnMottakerInput`

The `AltinnMottakerInput` type (lines 1720–1723) is used in `MottakerInput` (line 1703) to specify who can see a notification using Altinn 2 serviceCode/serviceEdition.

**Action:** Add `@deprecated` directive to the `altinn` field in `MottakerInput`:

```graphql
input MottakerInput {
    altinn: AltinnMottakerInput @deprecated(reason: "Altinn 2 er avviklet. Bruk altinnRessurs i stedet.")
    altinnRessurs: AltinnRessursMottakerInput
    naermesteLeder: NaermesteLederMottakerInput
}
```

Update the doc comment on `AltinnMottakerInput` (lines 1708–1719) to clearly state it is no longer accepted:

```graphql
"""
DEPRECATED: Altinn 2 er avviklet. Bruk AltinnRessursMottakerInput i stedet.

Denne mottakeren er ikke lenger gyldig og vil returnere UgyldigMottaker ved bruk.
"""
input AltinnMottakerInput {
    serviceCode: String!
    serviceEdition: String!
}
```

### 1b. Deprecate `EksterntVarselAltinntjenesteInput`

The `altinntjeneste` field in `EksterntVarselInput` (line 1518) allows producers to send external notifications via Altinn 2 service.

**Action:** Add `@deprecated` directive:

```graphql
input EksterntVarselInput {
    sms: EksterntVarselSmsInput
    epost: EksterntVarselEpostInput
    altinntjeneste: EksterntVarselAltinntjenesteInput @deprecated(reason: "Altinn 2 er avviklet. Bruk altinnressurs i stedet.")
    altinnressurs: EksterntVarselAltinnressursInput
}
```

Also update the doc comment on `EksterntVarselAltinntjenesteInput` (lines 1596–1614) and `AltinntjenesteMottakerInput` (lines 1685–1693) to state they are deprecated.

### 1c. Deprecate `PaaminnelseEksterntVarselAltinntjenesteInput`

The `altinntjeneste` field in `PaaminnelseEksterntVarselInput` (line 1424) allows Altinn 2 notifications on reminders.

**Action:** Add `@deprecated` directive:

```graphql
input PaaminnelseEksterntVarselInput {
    sms: PaaminnelseEksterntVarselSmsInput
    epost: PaaminnelseEksterntVarselEpostInput
    altinntjeneste: PaaminnelseEksterntVarselAltinntjenesteInput @deprecated(reason: "Altinn 2 er avviklet. Bruk altinnressurs i stedet.")
    altinnressurs: PaaminnelseEksterntVarselAltinnressursInput
}
```

### 1d. Deprecate `AltinnMottaker` output type doc

The `AltinnMottaker` output type (lines 176–180) and its membership in the `Mottaker` union (line 172) must remain for historical data. But update the doc comment:

```graphql
"""
DEPRECATED: Representerer en Altinn 2-mottaker. Kun relevant for historiske notifikasjoner.
Nye notifikasjoner bør bruke AltinnRessursMottaker.
"""
type AltinnMottaker {
    serviceCode: String!
    serviceEdition: String!
    virksomhetsnummer: String!
}
```

---

## Task 2: Reject Altinn 2 Inputs at Runtime

### 2a. Reject `altinn` in `MottakerInput` with explicit error

**File:** `app/src/main/kotlin/no/nav/arbeidsgiver/notifikasjon/produsent/api/NyNotifikasjonFelles.kt`

The `altinn` field stays on the data class (it's still in the GraphQL schema), but the `init` block now explicitly throws a `ValideringsFeil` if `altinn` is provided, before running the normal validator. The `tilHendelseModel()` method no longer considers `altinn`.

**Current code (line 255):**
```kotlin
internal data class MottakerInput(
    val altinn: AltinnMottakerInput?,
    val naermesteLeder: NaermesteLederMottakerInput?,
    val altinnRessurs: AltinnRessursMottakerInput?
) {
    init {
        Validators.ExactlyOneFieldGiven("MottakerInput")(
            mapOf(
                "altinn" to altinn,
                "naermesteLeder" to naermesteLeder,
                "altinnRessurs" to altinnRessurs,
            )
        )
    }

    fun tilHendelseModel(
        virksomhetsnummer: String,
    ): Mottaker {
        check(listOfNotNull(altinn, naermesteLeder, altinnRessurs).size == 1) {
            "Ugyldig mottaker"
        }
        return altinnRessurs?.tilDomene(virksomhetsnummer)
            ?: altinn?.tilDomene(virksomhetsnummer)
            ?: naermesteLeder?.tilDomene(virksomhetsnummer)
            ?: throw IllegalArgumentException("Ugyldig mottaker")
    }
}
```

**New code — explicitly throw validation error if altinn is present in input:**
```kotlin
internal data class MottakerInput(
    val altinn: AltinnMottakerInput?,
    val naermesteLeder: NaermesteLederMottakerInput?,
    val altinnRessurs: AltinnRessursMottakerInput?
) {
    init {
        if (altinn != null) {
            throw ValideringsFeil("MottakerInput: altinn er ikke lenger støttet. Altinn 2 er avviklet. Bruk altinnRessurs i stedet.")
        }
        Validators.ExactlyOneFieldGiven("MottakerInput")(
            mapOf(
                "naermesteLeder" to naermesteLeder,
                "altinnRessurs" to altinnRessurs,
            )
        )
    }

    fun tilHendelseModel(
        virksomhetsnummer: String,
    ): Mottaker {
        check(listOfNotNull(naermesteLeder, altinnRessurs).size == 1) {
            "Ugyldig mottaker"
        }
        return altinnRessurs?.tilDomene(virksomhetsnummer)
            ?: naermesteLeder?.tilDomene(virksomhetsnummer)
            ?: throw IllegalArgumentException("Ugyldig mottaker")
    }
}
```

This means:
- If a consumer sends `altinn: {...}` (with or without other fields), the `ValideringsFeil` is thrown immediately with a clear message directing them to use `altinnRessurs` instead.
- The `ValideringsFeil` exception is caught by the GraphQL error handling layer and returned as a user-facing validation error.
- If `altinn` is null, the normal `ExactlyOneFieldGiven` validator runs on the remaining fields (`naermesteLeder`, `altinnRessurs`).

### 2b. Reject `altinntjeneste` in `EksterntVarselInput` with explicit error

**File:** `app/src/main/kotlin/no/nav/arbeidsgiver/notifikasjon/produsent/api/NyNotifikasjonFelles.kt`

Same pattern as 2a. The `altinntjeneste` field stays on the data class but the `init` block explicitly throws `ValideringsFeil` if it is provided, before running the normal validator. The `tilHendelseModel()` method no longer considers `altinntjeneste`.

**code:**
```kotlin
internal data class EksterntVarselInput(
    val sms: Sms?,
    val epost: Epost?,
    val altinntjeneste: Altinntjeneste?,
    val altinnressurs: Altinnressurs?,
) {
    init {
        if (altinntjeneste != null) {
            throw ValideringsFeil("EksterntVarselInput: altinntjeneste er ikke lenger støttet. Altinn 2 er avviklet. Bruk altinnressurs i stedet.")
        }
        Validators.ExactlyOneFieldGiven("EksterntVarselInput")(
            mapOf(
                "sms" to sms,
                "epost" to epost,
                "altinnressurs" to altinnressurs
            )
        )
    }

    fun tilHendelseModel(virksomhetsnummer: String): EksterntVarsel {
        if (sms != null) { return sms.tilHendelseModel(virksomhetsnummer) }
        if (epost != null) { return epost.tilHendelseModel(virksomhetsnummer) }
        if (altinnressurs != null) { return altinnressurs.tilHendelseModel(virksomhetsnummer) }
        throw RuntimeException("Feil format")
    }
}
```

This means:
- If a consumer sends `altinntjeneste: {...}`, the `ValideringsFeil` is thrown immediately with a clear message directing them to use `altinnressurs` instead.
- If `altinntjeneste` is null, the normal `ExactlyOneFieldGiven` validator runs on the remaining fields (`sms`, `epost`, `altinnressurs`).

### 2c. Reject `altinntjeneste` in `PaaminnelseEksterntVarselInput` with explicit error

**File:** `app/src/main/kotlin/no/nav/arbeidsgiver/notifikasjon/produsent/api/NyNotifikasjonFelles.kt`

Same pattern as 2a and 2b.

**code:**
```kotlin
internal class PaaminnelseEksterntVarselInput(
    val sms: Sms?,
    val epost: Epost?,
    val altinntjeneste: Altinntjeneste?,
    val altinnressurs: Altinnressurs?,
) {
    init {
        if (altinntjeneste != null) {
            throw ValideringsFeil("PaaminnelseEksterntVarselInput: altinntjeneste er ikke lenger støttet. Altinn 2 er avviklet. Bruk altinnressurs i stedet.")
        }
        Validators.ExactlyOneFieldGiven("PaaminnelseEksterntVarselInput")(
            mapOf(
                "sms" to sms,
                "epost" to epost,
                "altinnressurs" to altinnressurs,
            )
        )
    }

    fun tilDomene(virksomhetsnummer: String): EksterntVarsel =
        when {
            sms != null -> sms.tilDomene(virksomhetsnummer)
            epost != null -> epost.tilDomene(virksomhetsnummer)
            altinnressurs != null -> altinnressurs.tilDomene(virksomhetsnummer)
            else -> error("graphql-validation failed, neither sms nor epost nor altinnressurs defined")
        }
}
```

This means:
- If a consumer sends `altinntjeneste: {...}` in a påminnelse, the `ValideringsFeil` is thrown immediately with a clear message directing them to use `altinnressurs` instead.
- If `altinntjeneste` is null, the normal `ExactlyOneFieldGiven` validator runs on the remaining fields (`sms`, `epost`, `altinnressurs`).

---

## Task 3: Remove Altinn 2 SOAP Client

### 3a. Delete `Altinn2VarselKlient.kt`

**File to delete:** `app/src/main/kotlin/no/nav/arbeidsgiver/notifikasjon/ekstern_varsling/Altinn2VarselKlient.kt`

This file contains:
- `Altinn2VarselKlient` interface (line 35)
- `Altinn2VarselKlientLogging` class (line 39) — logging-only implementation
- `Altinn2VarselKlientMedFilter` class (line 50) — dev environment filter
- `Altinn2VarselKlientImpl` class (line 81) — full SOAP client

### 3b. Delete `AltinnVarselKlientResponse.kt`

**File to delete:** `app/src/main/kotlin/no/nav/arbeidsgiver/notifikasjon/ekstern_varsling/AltinnVarselKlientResponse.kt`

Contains `AltinnVarselKlientResponse` (Ok, Feil) and `UkjentException` used exclusively by the Altinn 2 client.

### 3c. Delete the WSDL file

**File to delete:** `app/src/main/resources/wsdl/NotificationAgencyExternalBasic.svc.wsdl`

And potentially the entire `wsdl/` directory if it only contains this file.

### 3d. Delete generated SOAP sources

The `app/target/generated-sources/cxf/` directory contains Java sources generated from the WSDL. These are generated at build time by the `cxf-codegen-plugin`, so they will stop being generated once the WSDL and plugin are removed.

---

## Task 4: Remove Altinn 2 from `EksternVarslingService`

### 4a. Remove `altinn2VarselKlient` dependency

**File:** `app/src/main/kotlin/no/nav/arbeidsgiver/notifikasjon/ekstern_varsling/EksternVarslingService.kt`

- Remove the `altinn2VarselKlient: Altinn2VarselKlient` constructor parameter (line 107)
- Remove the `altinn2VarselHandler()` method (lines 360–424)
- In `workOnEksternVarsel()` (line 211–217), change the routing so that `EksternVarsel.Altinntjeneste` goes through the `altinn3VarselHandler` or is logged and skipped/failed. Since we are no longer accepting new Altinntjeneste varsler, any remaining ones in the queue should be handled gracefully — either:
  - **Option A:** Mark them as failed with a descriptive message ("Altinn 2 er avviklet")
  - **Option B:** Route them through the Altinn 3 handler if there's a migration path

  **Recommended: Option A** — fail them gracefully since they are historical artifacts.

### 4b. Remove Altinn 2 from `EksternVarsling.kt` startup

**File:** `app/src/main/kotlin/no/nav/arbeidsgiver/notifikasjon/ekstern_varsling/EksternVarsling.kt`

- Remove the `altinn2VarselKlient` instantiation block (lines 56–66)
- Remove the `internalTestClient` setup and the `/internal/send_sms` and `/internal/send_epost` test endpoints (lines 90–101)
- Remove the `testSms()` and `testEpost()` functions and their request body data classes (lines 111–160+)

---

## Task 5: Remove Apache CXF Dependencies

### 5a. Remove CXF Maven dependencies from `pom.xml`

**File:** `app/pom.xml`

Remove these dependencies (lines 125–153):
```xml
<!-- altinn-ws -->
<dependency>
    <groupId>org.apache.cxf</groupId>
    <artifactId>cxf-rt-frontend-jaxws</artifactId>
    ...
</dependency>
<dependency>
    <groupId>org.apache.cxf</groupId>
    <artifactId>cxf-rt-features-logging</artifactId>
    ...
</dependency>
<dependency>
    <groupId>org.apache.cxf</groupId>
    <artifactId>cxf-rt-transports-http</artifactId>
    ...
</dependency>
<dependency>
    <groupId>org.apache.cxf</groupId>
    <artifactId>cxf-rt-transports-http-jetty</artifactId>
    <scope>test</scope>
    ...
</dependency>
```

### 5b. Remove CXF codegen plugin

**File:** `app/pom.xml`

Remove the `cxf-codegen-plugin` configuration (around line 282–296):
```xml
<plugin>
    <groupId>org.apache.cxf</groupId>
    <artifactId>cxf-codegen-plugin</artifactId>
    ...
</plugin>
```

### 5c. Remove the `cxf.version` property

**File:** `app/pom.xml`

Remove the CXF version property from the `<properties>` section.

---

## Task 6: Remove Altinn 2 from NAIS Configuration

### 6a. Remove `altinn-varsel-firewall` access rule

**Files:**
- `app/nais/prod-gcp-ekstern-varsling.yaml` — remove line 49: `- application: altinn-varsel-firewall`
- `app/nais/dev-gcp-ekstern-varsling.yaml` — remove line 49: `- application: altinn-varsel-firewall`

### 6b. Remove `altinn-basic-ws` secret

**Files:**
- `app/nais/prod-gcp-ekstern-varsling.yaml` — remove line 65: `- secret: altinn-basic-ws`
- `app/nais/dev-gcp-ekstern-varsling.yaml` — remove line 66: `- secret: altinn-basic-ws`

**Note:** The `ALTINN_3_API_BASE_URL` env var, `platform.altinn.no` external host, and maskinporten scope `altinn:serviceowner/notifications.create` are for Altinn **3** and must remain.

---

## Task 7: Remove Altinn 2 Test Code

### 7a. Delete `Altinn2VarselKlientImplTest.kt`

**File to delete:** `app/src/test/kotlin/no/nav/arbeidsgiver/notifikasjon/ekstern_varsling/Altinn2VarselKlientImplTest.kt`

### 7b. Delete `MockAltinnServer.kt`

**File to delete:** `app/src/test/kotlin/no/nav/arbeidsgiver/notifikasjon/ekstern_varsling/MockAltinnServer.kt`

### 7c. Update `EksternVarslingServiceTest.kt`

**File:** `app/src/test/kotlin/no/nav/arbeidsgiver/notifikasjon/ekstern_varsling/EksternVarslingServiceTest.kt`

- Remove the mock `Altinn2` type (around line 48) and the mock Altinn2 client implementation (around lines 76–80)
- Remove or update test cases that specifically test Altinn 2 behavior (line 382 and related)
- Add a test that verifies `EksternVarsel.Altinntjeneste` is handled gracefully (failed/skipped)

### 7d. Update `EksempelHendelser.kt`

**File:** `app/src/test/kotlin/no/nav/arbeidsgiver/notifikasjon/util/EksempelHendelser.kt`

Keep the Altinn 2 example event data (since events are unchanged), but ensure test utilities still compile without the removed client code.

### 7e. Update `DataproduktEksterVarselVellykketTest.kt`

**File:** `app/src/test/kotlin/no/nav/arbeidsgiver/notifikasjon/dataprodukt/DataproduktEksterVarselVellykketTest.kt`

Keep tests that verify parsing of historical Altinn 2 SOAP response data (since we still need to handle these in the event stream). Remove tests that depend on the removed Altinn 2 client.

### 7f. Update `EksternVarselApiTest.kt`

**File:** `app/src/test/kotlin/no/nav/arbeidsgiver/notifikasjon/produsent/api/EksternVarselApiTest.kt`

Update API tests to verify that `altinntjeneste` input now returns `UgyldigMottaker`.

### 7g. Update `NyOppgavePaaminnelseTest.kt`

**File:** `app/src/test/kotlin/no/nav/arbeidsgiver/notifikasjon/produsent/api/NyOppgavePaaminnelseTest.kt`

Update reminder tests to verify that `altinntjeneste` påminnelse input now returns an error.

---

## Task 8: Clean Up EksternVarslingModel

### 8a. Remove `EksternVarsel.Altinntjeneste` from runtime model

**File:** `app/src/main/kotlin/no/nav/arbeidsgiver/notifikasjon/ekstern_varsling/EksternVarslingModel.kt`

The `EksternVarsel.Altinntjeneste` data class (lines 46–54) is the **runtime** representation used by the sending service. Since we no longer send via Altinn 2, this class can be removed.

**However**, the `EksternVarslingRepository` reads historical data from the database and constructs `EksternVarsel.Altinntjeneste` instances (line 725). Two options:

- **Option A (recommended):** Keep `EksternVarsel.Altinntjeneste` in the model but ensure it is rejected/failed in the service handler. This is safest for historical data in the queue.
- **Option B:** Remove it and change the repository to skip/fail Altinntjeneste rows on read.

**Recommended: Option A** — keep the model class but fail it in the service.

---

## Task 9: Remove `ServicecodeDefinisjon` from ProdusentRegister

### 9a. Remove `ServicecodeDefinisjon` entries from all produsent definitions

**File:** `app/src/main/kotlin/no/nav/arbeidsgiver/notifikasjon/infrastruktur/produsenter/ProdusentRegister.kt`

Remove all `ServicecodeDefinisjon(...)` entries from every `Produsent`'s `tillatteMottakere` list in the `PRODUSENTER` configuration. This ensures no produsent is allowed to send to an `AltinnMottaker`, providing a second line of defense alongside the `MottakerInput` validation change in Task 2a.

Known occurrences (search for `ServicecodeDefinisjon(` to find all):
- Line 171: `ServicecodeDefinisjon(code = "4936", version = "1")`
- Lines 172–174: `ServicecodeDefinisjon(code = "5516", version = "1"/"2"/"3")`
- Lines 205–215: Multiple `ServicecodeDefinisjon` entries
- Lines 256, 273, 299, 322, 347–348, 379, 406, 433: Additional entries across various produsenter

Remove each `ServicecodeDefinisjon(...)` entry from the lists. If a produsent's `tillatteMottakere` becomes empty after removal (i.e. it only had `ServicecodeDefinisjon` entries and no `RessursIdDefinisjon` or `NærmesteLederDefinisjon`), verify whether that produsent is still active and should be migrated to Altinn 3 resources.

### 9b. Remove the `ServicecodeDefinisjon` class itself

Once all entries are removed, delete the `ServicecodeDefinisjon` data class (lines 35–55) and the `MottakerRegister.servicecodeDefinisjoner` property (lines 86–89). Also remove the `erDefinert` branch for `ServicecodeDefinisjon` (line 98).

Remove the `AltinnMottaker` import (line 3) if it is no longer referenced after removing `ServicecodeDefinisjon.akseptererMottaker()`.

### 9c. Remove `AltinnMottakerInput` class and related code

**File:** `app/src/main/kotlin/no/nav/arbeidsgiver/notifikasjon/produsent/api/NyNotifikasjonFelles.kt`

After Task 2a removes `altinn` from the validation/resolution logic, the `AltinnMottakerInput` data class (lines 243–253) and its `tilDomene()` method become dead code. Remove the class entirely.

---

## DO NOT MODIFY (Important!)

The following must remain unchanged:

1. **`Hendelse.kt` event model types:**
   - `HendelseModel.AltinnMottaker` (lines 728–733) — part of Kafka event schema
   - `HendelseModel.AltinntjenesteVarselKontaktinfo` (lines 769–780) — part of Kafka event schema

2. **Database Flyway migrations** — all existing `V*.sql` files in `db/migration/`

3. **`EksternVarslingRepository.kt` read paths** — the repository must still be able to read historical Altinntjeneste data from the database (line 725) and construct the runtime model. Only the *write path* (line 341, `insertAltinntjenesteVarsel`) becomes dead code once no new events are accepted.

4. **`Mottaker` union in GraphQL** — `AltinnMottaker` must remain in the union for query responses (historical data):
   ```graphql
   union Mottaker = AltinnMottaker | NaermesteLederMottaker | AltinnRessursMottaker
   ```

5. **`QueryNotifikasjoner.AltinnMottaker`** — the output mapping for historical notifications must remain.

---

## Suggested Execution Order

1. **Task 1** — Deprecate GraphQL types (schema-only, low risk)
2. **Task 2** — Remove altinn from MottakerInput validation, reject Altinn 2 varsel inputs
3. **Task 9** — Remove ServicecodeDefinisjon from ProdusentRegister and AltinnMottakerInput class
4. **Task 7f, 7g** — Update API tests to expect errors for Altinn 2 inputs
5. **Task 3** — Delete Altinn 2 SOAP client code
6. **Task 4** — Remove Altinn 2 from EksternVarslingService
7. **Task 5** — Remove CXF dependencies from pom.xml
8. **Task 6** — Remove NAIS config (altinn-varsel-firewall, altinn-basic-ws secret)
9. **Task 7a–7e** — Remove/update remaining test code
10. **Task 8** — Clean up EksternVarslingModel (if doing Option B)

## Verification

After all changes:
1. `mvn clean compile` must succeed (no compilation errors from removed classes)
2. `mvn test` must pass (all tests green)
3. GraphQL introspection must show `@deprecated` on the Altinn 2 fields
4. Submitting an `AltinnMottakerInput` to any mutation must return `UgyldigMottaker`
5. Submitting an `altinntjeneste` eksternt varsel must return an error
6. The application must start and process Altinn 3 notifications normally
7. Historical notifications with Altinn 2 mottaker/varsler must still be queryable

## Files Changed (Summary)

| Action | File |
|--------|------|
| Modify | `app/src/main/resources/produsent.graphql` |
| Modify | `app/src/main/kotlin/.../produsent/api/NyNotifikasjonFelles.kt` |
| Modify | `app/src/main/kotlin/.../produsent/api/Util.kt` (or mutation files) |
| Modify | `app/src/main/kotlin/.../ekstern_varsling/EksternVarslingService.kt` |
| Modify | `app/src/main/kotlin/.../ekstern_varsling/EksternVarsling.kt` |
| Modify | `app/src/main/kotlin/.../ekstern_varsling/EksternVarslingModel.kt` |
| Modify | `app/pom.xml` |
| Modify | `app/nais/prod-gcp-ekstern-varsling.yaml` |
| Modify | `app/nais/dev-gcp-ekstern-varsling.yaml` |
| Delete | `app/src/main/kotlin/.../ekstern_varsling/Altinn2VarselKlient.kt` |
| Delete | `app/src/main/kotlin/.../ekstern_varsling/AltinnVarselKlientResponse.kt` |
| Delete | `app/src/main/resources/wsdl/NotificationAgencyExternalBasic.svc.wsdl` |
| Delete | `app/src/test/kotlin/.../ekstern_varsling/Altinn2VarselKlientImplTest.kt` |
| Delete | `app/src/test/kotlin/.../ekstern_varsling/MockAltinnServer.kt` |
| Modify | `app/src/test/kotlin/.../ekstern_varsling/EksternVarslingServiceTest.kt` |
| Modify | `app/src/test/kotlin/.../util/EksempelHendelser.kt` |
| Modify | `app/src/test/kotlin/.../dataprodukt/DataproduktEksterVarselVellykketTest.kt` |
| Modify | `app/src/test/kotlin/.../produsent/api/EksternVarselApiTest.kt` |
| Modify | `app/src/test/kotlin/.../produsent/api/NyOppgavePaaminnelseTest.kt` |
| Keep   | `app/src/main/kotlin/.../hendelse/Hendelse.kt` (unchanged) |
| Keep   | `app/src/main/kotlin/.../ekstern_varsling/EksternVarslingRepository.kt` (read paths) |
| Modify | `app/src/main/kotlin/.../infrastruktur/produsenter/ProdusentRegister.kt` |
