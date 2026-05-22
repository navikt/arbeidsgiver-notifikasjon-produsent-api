# Specification: Route Migrated Altinntjeneste Varsler to Altinn 3

## Background

Service code `4936:1` has been migrated from Altinn 2 to Altinn 3 ressurser. API calls for ekstern varsling using `4936:1` via the Altinn 2 SOAP client now fail. However, scheduled notifications (`påminnelser`) created before the migration still arrive as `EksternVarsel.Altinntjeneste` with `serviceCode=4936, serviceEdition=1`.

We need to intercept these varsler and route them through Altinn 3 instead. Altinn 2 code remains untouched — only the routing decision in `workOnEksternVarsel` changes.

## Scope

This specification covers **only** the routing logic in `workOnEksternVarsel` in `EksternVarslingService.kt`. The Altinn 2 client, handler, and all other code remain as-is.

---

## Current Behavior

**File:** `app/src/main/kotlin/no/nav/arbeidsgiver/notifikasjon/ekstern_varsling/EksternVarslingService.kt` (lines 210-217)

```kotlin
withContext(varsel.asMDCContext()) {
    when (varsel.data.eksternVarsel) {
        is EksternVarsel.Altinntjeneste -> altinn2VarselHandler(varsel)
        is EksternVarsel.Sms,
        is EksternVarsel.Epost,
        is EksternVarsel.Altinnressurs -> altinn3VarselHandler(varsel)
    }
}
```

All `Altinntjeneste` varsler go to `altinn2VarselHandler`, which sends via the Altinn 2 SOAP client. For migrated service codes like `4936:1`, this now fails because Altinn 2 no longer handles them.

## Desired Behavior

When an `EksternVarsel.Altinntjeneste` varsel is encountered:

1. Check if the `(serviceCode, serviceEdition)` is migrated using an `isMigrated` predicate.
2. If migrated, resolve the target `ressursId` (disambiguating with `produsentId` when needed), convert the varsel to `EksternVarsel.Altinnressurs`, and route it through `altinn3VarselHandler`.
3. If not migrated, continue sending via `altinn2VarselHandler` as before (unchanged behavior).
4. If migrated but the `ressursId` cannot be determined (e.g. unknown `produsentId` for `4936:1`), fall through to `altinn2VarselHandler`.

The `isMigrated` predicate serves as the single place to track migration progress. As more service codes are migrated from Altinn 2 to Altinn 3, they are added to this set. Once all service codes are migrated, Altinn 2 can be fully removed.

---

## Migration Tracking: `isMigrated`

The `isMigrated` predicate is a function that checks whether a given `(serviceCode, serviceEdition)` has been migrated to Altinn 3. It is the **single source of truth** for which service codes should be routed away from Altinn 2.

Initially, only `4936:1` is migrated. As other service codes are migrated, add them to this set. When all entries in the routing map are also in the migrated set, Altinn 2 can be fully removed.

```kotlin
private val migratedServiceCodes: Set<Pair<String, String>> = setOf(
    "4936" to "1",
)

private fun isMigrated(serviceCode: String, serviceEdition: String): Boolean =
    (serviceCode to serviceEdition) in migratedServiceCodes
```

---

## Routing Map

Implement the full `serviceCode:serviceEdition` to `ressursId` mapping. This is the same map as in `serviceCodeEditionTilRessursMap` in `Util.kt` (line 177), but defined locally in the ekstern varsling module. The map covers all known service code to ressurs mappings so that when a service code is added to `migratedServiceCodes`, the routing works immediately without further code changes.

| serviceCode:serviceEdition | ressursId |
|---|---|
| `5810:1` | `nav_permittering-og-nedbemmaning_innsyn-i-alle-innsendte-meldinger` |
| `5867:1` | `nav_sosialtjenester_digisos-avtale` |
| `3403:2` (prod) / `3403:1` (other) | `nav_forebygge-og-redusere-sykefravar_sykefravarsstatistikk` |
| `5332:2` (prod) / `5332:1` (other) | `nav_tiltak_arbeidstrening` |
| `2896:87` | `nav_utbetaling_endre-kontonummer-refusjon-arbeidsgiver` |
| `5516:1` | `nav_tiltak_midlertidig-lonnstilskudd` |
| `5516:2` | `nav_tiltak_varig-lonnstilskudd` |
| `5516:3` | `nav_tiltak_sommerjobb` |
| `5516:4` | `nav_tiltak_mentor` |
| `5516:5` | `nav_tiltak_inkluderingstilskudd` |
| `5516:6` | `nav_tiltak_varig-tilrettelagt-arbeid-ordinaer` |
| `5516:7` | `nav_tiltak_adressesperre` |
| `5278:1` | `nav_tiltak_tilskuddsbrev` |
| `5384:1` | `nav_tiltak_ekspertbistand` |
| `5078:1` | `nav_rekruttering_kandidater` |
| `5902:1` | `nav_yrkesskade_skademelding` |
| `5441:1` | `nav_arbeidsforhold_aa-registeret-innsyn-arbeidsgiver` |
| `5441:2` | `nav_arbeidsforhold_aa-registeret-brukerstotte` |
| `5719:1` | `nav_arbeidsforhold_aa-registeret-sok-tilgang` |
| `5723:1` | `nav_arbeidsforhold_aa-registeret-oppslag-samarbeidspartnere` |
| **`4936:1`** | **Ambiguous — requires produsentId disambiguation (see below)** |

### Disambiguating `4936:1` by produsentId

`4936:1` was shared by four producers, each migrated to a different ressurs:

| produsentId | ressursId |
|---|---|
| `fritakagp` | `nav_sykepenger_fritak-arbeidsgiverperiode` |
| `im-notifikasjon` | `nav_sykepenger_inntektsmelding` |
| `fp-inntektsmelding-notifikasjon` | `nav_foreldrepenger_inntektsmelding` |
| `k9-inntektsmelding-notifikasjon` | `nav_sykdom-i-familien_inntektsmelding` |

If `produsentId` is not in this table, fall through to `altinn2VarselHandler`.

---

## Conversion: Altinntjeneste to Altinnressurs

When a target `ressursId` is determined, create an `EksternVarsel.Altinnressurs` from the `EksternVarsel.Altinntjeneste` fields:

| Altinnressurs field | Source |
|---|---|
| `fnrEllerOrgnr` | `altinntjeneste.fnrEllerOrgnr` |
| `sendeVindu` | `altinntjeneste.sendeVindu` |
| `sendeTidspunkt` | `altinntjeneste.sendeTidspunkt` |
| `resourceId` | Looked up from routing map |
| `epostTittel` | `altinntjeneste.tittel` |
| `epostInnhold` | `altinntjeneste.innhold` |
| `smsInnhold` | `altinntjeneste.tittel` |
| `ordreId` | `null` |

The converted varsel is passed to `altinn3VarselHandler`, which calls `OrderRequest.from` — since the varsel is now an `Altinnressurs`, it goes through the `Organization.from` path in `Altinn3VarselKlient.kt`.

---

## Implementation Approach

### Step 1: Define routing maps in `EksternVarslingService.kt`

Define a local map (self-contained, no changes to `Util.kt`):

```kotlin
private val altinntjenesteToRessursMap: Map<Pair<String, String>, List<String>> = mapOf(
    "nav_permittering-og-nedbemmaning_innsyn-i-alle-innsendte-meldinger" to ("5810" to "1"),
    "nav_sosialtjenester_digisos-avtale" to ("5867" to "1"),
    "nav_forebygge-og-redusere-sykefravar_sykefravarsstatistikk" to ("3403" to basedOnEnv(
        prod = { "2" },
        other = { "1" })),
    "nav_tiltak_arbeidstrening" to ("5332" to basedOnEnv(prod = { "2" }, other = { "1" })),
    "nav_utbetaling_endre-kontonummer-refusjon-arbeidsgiver" to ("2896" to "87"),
    "nav_tiltak_midlertidig-lonnstilskudd" to ("5516" to "1"),
    "nav_tiltak_varig-lonnstilskudd" to ("5516" to "2"),
    "nav_tiltak_sommerjobb" to ("5516" to "3"),
    "nav_tiltak_mentor" to ("5516" to "4"),
    "nav_tiltak_inkluderingstilskudd" to ("5516" to "5"),
    "nav_tiltak_varig-tilrettelagt-arbeid-ordinaer" to ("5516" to "6"),
    "nav_tiltak_adressesperre" to ("5516" to "7"),
    "nav_tiltak_tilskuddsbrev" to ("5278" to "1"),
    "nav_tiltak_ekspertbistand" to ("5384" to "1"),
    "nav_foreldrepenger_inntektsmelding" to ("4936" to "1"),
    "nav_sykepenger_inntektsmelding" to ("4936" to "1"),
    "nav_sykepenger_fritak-arbeidsgiverperiode" to ("4936" to "1"),
    "nav_sykdom-i-familien_inntektsmelding" to ("4936" to "1"),
    "nav_arbeidsforhold_aa-registeret-innsyn-arbeidsgiver" to ("5441" to "1"),
    "nav_arbeidsforhold_aa-registeret-brukerstotte" to ("5441" to "2"),
    "nav_arbeidsforhold_aa-registeret-sok-tilgang" to ("5719" to "1"),
    "nav_arbeidsforhold_aa-registeret-oppslag-samarbeidspartnere" to ("5723" to "1"),
    "nav_rekruttering_kandidater" to ("5078" to "1"),
    "nav_yrkesskade_skademelding" to ("5902" to "1"),
).entries.groupBy({ it.value }, { it.key })

private val produsentIdTilRessursForServiceCode4936 = mapOf(
    "fritakagp" to "nav_sykepenger_fritak-arbeidsgiverperiode",
    "im-notifikasjon" to "nav_sykepenger_inntektsmelding",
    "fp-inntektsmelding-notifikasjon" to "nav_foreldrepenger_inntektsmelding",
    "k9-inntektsmelding-notifikasjon" to "nav_sykdom-i-familien_inntektsmelding",
)
```

### Step 2: Implement `resolveRessursId`

```kotlin
private fun resolveRessursId(serviceCode: String, serviceEdition: String, produsentId: String): String? {
    val candidates = altinntjenesteToRessursMap[serviceCode to serviceEdition] ?: return null
    return when {
        candidates.size == 1 -> candidates.single()
        candidates.size > 1 -> produsentIdTilRessursForServiceCode4936[produsentId]
        else -> null
    }
}
```

### Step 3: Update `workOnEksternVarsel`

Replace the `EksternVarsel.Altinntjeneste` branch:

```kotlin
is EksternVarsel.Altinntjeneste -> {
    val altinntjeneste = varsel.data.eksternVarsel as EksternVarsel.Altinntjeneste
    if (!isMigrated(altinntjeneste.serviceCode, altinntjeneste.serviceEdition)) {
        altinn2VarselHandler(varsel)
        return@withContext
    }
    val ressursId = resolveRessursId(
        serviceCode = altinntjeneste.serviceCode,
        serviceEdition = altinntjeneste.serviceEdition,
        produsentId = varsel.data.produsentId,
    )
    if (ressursId != null) {
        log.info("Ruter Altinntjeneste-varsel ${varsel.data.varselId} til ressurs $ressursId")
        val convertedVarsel = varsel.withEksternVarsel(
            EksternVarsel.Altinnressurs(
                fnrEllerOrgnr = altinntjeneste.fnrEllerOrgnr,
                sendeVindu = altinntjeneste.sendeVindu,
                sendeTidspunkt = altinntjeneste.sendeTidspunkt,
                resourceId = ressursId,
                epostTittel = altinntjeneste.tittel,
                epostInnhold = altinntjeneste.innhold,
                smsInnhold = altinntjeneste.tittel,
                ordreId = null,
            )
        )
        altinn3VarselHandler(convertedVarsel)
    } else {
        log.error("Migrert serviceCode ${altinntjeneste.serviceCode}:${altinntjeneste.serviceEdition} " +
            "men kan ikke bestemme ressursId for produsentId=${varsel.data.produsentId}. " +
            "Faller tilbake til altinn2.")
        altinn2VarselHandler(varsel)
    }
}
```

The flow is:
1. **`isMigrated` check first** — if the service code is not migrated, go straight to `altinn2VarselHandler` (no lookup needed).
2. **Resolve ressursId** — if migrated, look up the target ressursId.
3. **Route or fall back** — if ressursId is found, convert and route to Altinn 3. If not (e.g. unknown produsentId for `4936:1`), log an error and fall back to `altinn2VarselHandler`.

Key difference from the `rm_altinn_2` spec: the fallback is always `altinn2VarselHandler(varsel)`, not a failure. Non-migrated service codes never hit the routing logic at all.

### Step 4: Implement `withEksternVarsel` on `EksternVarselTilstand`

Add a helper to `EksternVarslingModel.kt` (or inline in the service):

```kotlin
fun EksternVarselTilstand.withEksternVarsel(newVarsel: EksternVarsel): EksternVarselTilstand {
    val newData = data.copy(eksternVarsel = newVarsel)
    return when (this) {
        is EksternVarselTilstand.Ny -> copy(data = newData)
        is EksternVarselTilstand.Sendt -> copy(data = newData)
        is EksternVarselTilstand.Kansellert -> copy(data = newData)
        is EksternVarselTilstand.Kvittert -> copy(data = newData)
    }
}
```

---

## Key Files

| File | Change |
|---|---|
| `app/src/main/kotlin/.../ekstern_varsling/EksternVarslingService.kt` | Add routing maps, `resolveRessursId`, update `workOnEksternVarsel` |
| `app/src/main/kotlin/.../ekstern_varsling/EksternVarslingModel.kt` | Add `withEksternVarsel` helper |

No other files are modified. The Altinn 2 client, handler, SOAP code, and all other behavior remain unchanged.

## Testing

### `isMigrated` predicate
1. `isMigrated("4936", "1")` returns `true`.
2. `isMigrated("5810", "1")` returns `false` (in routing map but not yet migrated).
3. `isMigrated("9999", "1")` returns `false` (unknown service code).

### Routing for migrated service codes
4. `Altinntjeneste` varsel with `4936:1` and `produsentId=fritakagp` is routed to `altinn3VarselHandler` with `ressursId=nav_sykepenger_fritak-arbeidsgiverperiode`.
5. `Altinntjeneste` varsel with `4936:1` and `produsentId=im-notifikasjon` is routed to `altinn3VarselHandler` with `ressursId=nav_sykepenger_inntektsmelding`.
6. `Altinntjeneste` varsel with `4936:1` and `produsentId=fp-inntektsmelding-notifikasjon` is routed to `altinn3VarselHandler` with `ressursId=nav_foreldrepenger_inntektsmelding`.
7. `Altinntjeneste` varsel with `4936:1` and `produsentId=k9-inntektsmelding-notifikasjon` is routed to `altinn3VarselHandler` with `ressursId=nav_sykdom-i-familien_inntektsmelding`.
8. `Altinntjeneste` varsel with `4936:1` and an unknown `produsentId` falls through to `altinn2VarselHandler`.

### Non-migrated service codes
9. `Altinntjeneste` varsel with a non-migrated `serviceCode:serviceEdition` (e.g. `5810:1`) goes directly to `altinn2VarselHandler` without hitting the routing map.
10. `Altinntjeneste` varsel with an unknown `serviceCode:serviceEdition` (not in any map) goes to `altinn2VarselHandler`.

### Future migration scenario
11. When `5810:1` is added to `migratedServiceCodes`, an `Altinntjeneste` varsel with `5810:1` is routed to `altinn3VarselHandler` with `ressursId=nav_permittering-og-nedbemmaning_innsyn-i-alle-innsendte-meldinger`.

### Field mapping
12. Verify the converted `Altinnressurs` has correct `epostTittel`, `epostInnhold`, `smsInnhold` from the original `Altinntjeneste` fields.

## DO NOT MODIFY

- `Hendelse.kt` event model types
- Database Flyway migrations
- `EksternVarslingRepository.kt`
- `Altinn2VarselKlient.kt` and `altinn2VarselHandler`
- The `EksternVarsel.Altinntjeneste` data class
- Any NAIS configuration
