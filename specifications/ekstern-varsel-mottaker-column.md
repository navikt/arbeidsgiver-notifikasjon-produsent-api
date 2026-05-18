# Add `mottaker` column to `ekstern_varsel` (dataprodukt)

## Background

Producers are migrating from the sunset `AltinntjenesteVarselKontaktinfo` to `AltinnressursVarselKontaktinfo`. We need visibility in Metabase dashboards to track which producers still send to Altinn tjeneste and to which `serviceCode:serviceEdition` they are sending. This helps ensure teams don't forget to also update their eksternvarsling order code.

## Changes

### 1. SQL migration: `V24__ekstern_varsel_mottaker.sql`

Add a nullable `mottaker` column to the `ekstern_varsel` table:

```sql
alter table ekstern_varsel
    add column mottaker text;
```

No backfill needed — existing rows stay `NULL`. New rows will be populated going forward.

### 2. Kotlin: `DataproduktModel.kt` — `opprettVarselBestilling`

Update the insert statement to include the new `mottaker` column (15th parameter). Set the value per kontaktinfo type:

| Type | `mottaker` value |
|---|---|
| `EpostVarselKontaktinfo` | `null` (PII — contains email address) |
| `SmsVarselKontaktinfo` | `null` (PII — contains phone number) |
| `AltinntjenesteVarselKontaktinfo` | `"$serviceCode:$serviceEdition"` |
| `AltinnressursVarselKontaktinfo` | `ressursId` |

In the insert SQL, add `mottaker` to the column list and `?` to values. In each `when` branch, add a `nullableText(...)` call with the appropriate value.

### 3. Terraform: `bigquery.tf` — ekstern_varsel data transfer

Add `mottaker` to both the inner PostgreSQL query and the outer BigQuery SELECT in the `ekstern_varsel` scheduled query.

### 4. Terraform: `bigquery.tf` — ekstern_varsel_view

Add `mottaker` to the SELECT list in the `ekstern_varsel_view` view definition.

## Files to change

| File | Change |
|---|---|
| `app/src/main/resources/db/migration/dataprodukt_model/V24__ekstern_varsel_mottaker.sql` | New migration — `alter table ekstern_varsel add column mottaker text` |
| `app/src/main/kotlin/no/nav/arbeidsgiver/notifikasjon/dataprodukt/DataproduktModel.kt` | Add `mottaker` to insert + set value per type |
| `terraform/bigquery.tf` (data transfer, ~line 440) | Add `mottaker` to data transfer query |
| `terraform/bigquery.tf` (view, ~line 619) | Add `mottaker` to view SELECT |

## What this enables

A Metabase dashboard query like:

```sql
SELECT produsent_id, merkelapp, mottaker, count(*)
FROM ekstern_varsel
WHERE varsel_type = 'ALTINN_TJENESTE'
GROUP BY produsent_id, merkelapp, mottaker
```

This shows at a glance which producers still use the sunset Altinn tjeneste path and which `serviceCode:serviceEdition` they target — making it easy to follow up with teams that need to migrate.
