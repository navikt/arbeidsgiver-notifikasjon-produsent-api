# Specification: Route Altinntjeneste EksternVarsel to Altinnressurs

## Background

Altinn 2 is being removed. Producers have migrated their API calls and now use `ressursId` instead of `serviceCode`/`serviceEdition`. However, scheduled external notifications (e.g. `påminnelser`) may still arrive as `EksternVarsel.Altinntjeneste` because they were created before the migration.

Currently, `workOnEksternVarsel` in `EksternVarslingService.kt` (line 211) unconditionally fails these with error code `altinn2_avviklet`. Instead, we want to **attempt to route** the varsel to the corresponding Altinn 3 ressurs, and only fail if the routing cannot be determined.

## Scope

This specification covers **only** the change to `workOnEksternVarsel` in `EksternVarslingService.kt`. No other files or behaviors are affected.

---

## Current Behavior

**File:** `app/src/main/kotlin/no/nav/arbeidsgiver/notifikasjon/ekstern_varsling/EksternVarslingService.kt` (lines 210-219)

```kotlin
when (varsel.data.eksternVarsel) {
    is EksternVarsel.Altinntjeneste -> {
        log.error("Altinn 2 er avviklet. Markerer varsel ${varsel.data.varselId} som feilet.")
        val feilResponse = Altinn3VarselKlient.ErrorResponse(...)
        eksternVarslingRepository.markerSomSendtAndReleaseJob(varsel.data.varselId, feilResponse)
    }
    is EksternVarsel.Sms,
    is EksternVarsel.Epost,
    is EksternVarsel.Altinnressurs -> altinn3VarselHandler(varsel)
}
```

All `Altinntjeneste` varsler are marked as failed immediately.

## Desired Behavior

When an `EksternVarsel.Altinntjeneste` varsel is encountered:

1. Look up the `(serviceCode, serviceEdition)` pair in a routing map to find the target `ressursId`.
2. If exactly one `ressursId` is found, convert the varsel to an `EksternVarsel.Altinnressurs` and route it through the normal `altinn3VarselHandler`.
3. If multiple `ressursId` candidates are found (the `4936:1` case), use the `produsentId` from `varsel.data.produsentId` to disambiguate.
4. If no `ressursId` can be determined, fail the varsel as today (with the `altinn2_avviklet` error).

---

## Routing Map

The existing `serviceCodeEditionTilRessursMap` in `app/src/main/kotlin/no/nav/arbeidsgiver/notifikasjon/produsent/api/Util.kt` (line 177) already contains the mapping from `ressursId` to `(serviceCode, serviceEdition)`, grouped as `Map<Pair<String, String>, List<String>>` — i.e. `(serviceCode, serviceEdition) -> List<ressursId>`.

For most service code/edition pairs, this list contains exactly one ressursId. The only ambiguous case is `4936:1`, which maps to four ressursId values:

| serviceCode:serviceEdition | ressursId(s) |
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
| **`4936:1`** | **`nav_foreldrepenger_inntektsmelding`, `nav_sykepenger_inntektsmelding`, `nav_sykepenger_fritak-arbeidsgiverperiode`, `nav_sykdom-i-familien_inntektsmelding`** |

### Disambiguating `4936:1` by produsentId

When the lookup for `(serviceCode, serviceEdition)` returns multiple ressursId candidates, use `varsel.data.produsentId` to select the correct one:

| produsentId | ressursId |
|---|---|
| `fritakagp` | `nav_sykepenger_fritak-arbeidsgiverperiode` |
| `im-notifikasjon` | `nav_sykepenger_inntektsmelding` |
| `fp-inntektsmelding-notifikasjon` | `nav_foreldrepenger_inntektsmelding` |
| `k9-inntektsmelding-notifikasjon` | `nav_sykdom-i-familien_inntektsmelding` |

If the `produsentId` is not in this table, fail the varsel.

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

Then replace `varsel.data.eksternVarsel` with the new `Altinnressurs` instance and pass the varsel to `altinn3VarselHandler`.

Note: The `OrderRequest.from` function in `Altinn3VarselKlient.kt` (line 274) currently throws `UnsupportedOperationException` for `Altinntjeneste`. After this change, the converted varsel will be an `Altinnressurs`, so it will go through the `Organization.from` path (line 271) as normal.

---

## Implementation Approach

### Step 1: Make the routing map accessible

The `serviceCodeEditionTilRessursMap` in `Util.kt` is currently `private`. So
- Define a separate routing map directly in `EksternVarslingService.kt` (duplicated but self-contained).

### Step 2: Add produsentId-to-ressursId disambiguation map

Create a map for the `4936:1` case:

```kotlin
private val produsentIdTilRessursForServiceCode4936 = mapOf(
    "fritakagp" to "nav_sykepenger_fritak-arbeidsgiverperiode",
    "im-notifikasjon" to "nav_sykepenger_inntektsmelding",
    "fp-inntektsmelding-notifikasjon" to "nav_foreldrepenger_inntektsmelding",
    "k9-inntektsmelding-notifikasjon" to "nav_sykdom-i-familien_inntektsmelding",
)
```

### Step 3: Update `workOnEksternVarsel`

Replace the `EksternVarsel.Altinntjeneste` branch in the `when` block:

```kotlin
is EksternVarsel.Altinntjeneste -> {
    val altinntjeneste = varsel.data.eksternVarsel as EksternVarsel.Altinntjeneste
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
        log.error("Kan ikke rute Altinntjeneste-varsel ${varsel.data.varselId} " +
            "(serviceCode=${altinntjeneste.serviceCode}, serviceEdition=${altinntjeneste.serviceEdition}, " +
            "produsentId=${varsel.data.produsentId}). Markerer som feilet.")
        val feilResponse = Altinn3VarselKlient.ErrorResponse(
            rå = TextNode.valueOf("Altinn 2 er avviklet"),
            code = "altinn2_avviklet",
            message = "Altinn 2 er avviklet. Kunne ikke bestemme hvilken ressurs varselet skal rutes til.",
        )
        eksternVarslingRepository.markerSomSendtAndReleaseJob(varsel.data.varselId, feilResponse)
    }
}
```

### Step 4: Implement `resolveRessursId`

```kotlin
private fun resolveRessursId(serviceCode: String, serviceEdition: String, produsentId: String): String? {
    val candidates = serviceCodeEditionTilRessursMap[serviceCode to serviceEdition] ?: return null
    return when {
        candidates.size == 1 -> candidates.single()
        candidates.size > 1 -> produsentIdTilRessursForServiceCode4936[produsentId]
        else -> null
    }
}
```

### Step 5: Implement `withEksternVarsel` on `EksternVarselTilstand`

The `EksternVarselTilstand` types (`Ny`, `Sendt`, `Kansellert`, `Kvittert`) hold an `EksternVarselStatiskData` which contains the `eksternVarsel`. A helper to create a copy with a replaced `eksternVarsel` is needed:

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
| `app/src/main/kotlin/.../ekstern_varsling/EksternVarslingService.kt` | Update `workOnEksternVarsel` with routing logic |
| `app/src/main/kotlin/.../ekstern_varsling/EksternVarslingModel.kt` | Add `withEksternVarsel` helper (optional, could be inline) |
| `app/src/main/kotlin/.../produsent/api/Util.kt` | Make `serviceCodeEditionTilRessursMap` accessible, or duplicate the map |

## Testing

1. Unit test: `Altinntjeneste` varsel with a known `serviceCode:serviceEdition` (e.g. `5810:1`) is routed to the correct `ressursId` and sent via `altinn3VarselHandler`.
2. Unit test: `Altinntjeneste` varsel with `4936:1` and `produsentId=fritakagp` is routed to `nav_sykepenger_fritak-arbeidsgiverperiode`.
3. Unit test: `Altinntjeneste` varsel with `4936:1` and an unknown `produsentId` is failed with `altinn2_avviklet`.
4. Unit test: `Altinntjeneste` varsel with an unknown `serviceCode:serviceEdition` is failed with `altinn2_avviklet`.
5. Verify field mapping: the converted `Altinnressurs` has correct `epostTittel`, `epostInnhold`, `smsInnhold` from the original `Altinntjeneste` fields.

## DO NOT MODIFY

- `Hendelse.kt` event model types (`AltinntjenesteVarselKontaktinfo`, `AltinnMottaker`)
- Database Flyway migrations
- `EksternVarslingRepository.kt` read paths for historical data
- The `EksternVarsel.Altinntjeneste` data class itself (still needed for deserialization)
