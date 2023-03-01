alter default privileges in schema public grant all on tables to cloudsqliamuser;
grant all on all tables in schema public to cloudsqliamuser;

create extension if not exists pgcrypto;

create table salt(salt text not null);
insert into salt(salt)
values (gen_salt('md5'));

create function pseudonymize(val text)
    returns text
as
$$
select crypt(val, (select salt from salt))::text;
$$
language sql
immutable;

create table aggregat_hendelse
(
    hendelse_id             uuid not null primary key,
    hendelse_type           text not null,
    aggregat_id             uuid not null,
    kilde_app_navn          text not null,
    virksomhetsnummer       text not null,
    virksomhetsnummer_pseud text generated always as (pseudonymize(virksomhetsnummer)) stored,
    produsent_id            text null,
    kafka_timestamp         text not null
);
revoke select (virksomhetsnummer) on aggregat_hendelse from public;

create table sak
(
    sak_id                  uuid not null primary key,
    grupperings_id          text not null,
    grupperings_id_pseud    text generated always as (pseudonymize(grupperings_id)) stored,
    virksomhetsnummer       text not null,
    virksomhetsnummer_pseud text generated always as (pseudonymize(virksomhetsnummer)) stored,
    produsent_id            text not null,
    merkelapp               text not null,
    tittel                  text not null,
    tittel_pseud            text generated always as (pseudonymize(tittel)) stored,
    lenke                   text not null,
    oppgitt_tidspunkt       text not null,
    mottatt_tidspunkt       text not null,
    soft_deleted_tidspunkt  text
);
revoke select (virksomhetsnummer, grupperings_id, tittel) on sak from public;

create table sak_status
(
    status_id                      uuid not null primary key, -- aka. hendelse_id
    idempotens_key                 text not null,
    sak_id                         uuid not null references sak (sak_id) on delete cascade,
    status                         text not null,
    overstyr_statustekst_med       text null,
    oppgitt_tidspunkt              text null,
    mottatt_tidspunkt              text not null,
    ny_lenke_til_sak               text null
);

create table hard_delete_bestilling
(
    aggregat_id uuid not null primary key references aggregat_hendelse(hendelse_id) on delete cascade,
    bestilling_type text not null, -- enum: OPPRETTELSE, STATUSENDRING, MANUELT
    bestilling_hendelsesid uuid not null,
    strategi text null, --- enum: FORLENG, OVERSKRIV
    spesifikasjon text not null,
    utregnet_tidspunkt text not null
);

create table notifikasjon
(
    notifikasjon_id                           uuid not null primary key,
    notifikasjon_type                         text not null, -- enum: BESKJED / OPPGAVE
    produsent_id                              text not null,
    merkelapp                                 text not null,
    ekstern_id                                text not null,
    tekst                                     text not null,
    tekst_pseud                               text generated always as (pseudonymize(tekst)) stored,
    grupperingsid                             text null,
    grupperingsid_pseud                       text generated always as (pseudonymize(grupperingsid)) stored,
    lenke                                     text not null,
    ny_lenke                                  text null,
    opprettet_tidspunkt                       text not null,
    soft_deleted_tidspunkt                    text,

    utgaatt_tidspunkt                         text,
    utfoert_tidspunkt                         text,
    paaminnelse_tidspunkt                     text,
    frist                                     date null,
    paaminnelse_bestilling_spesifikasjon_type text,          -- enum: KONKRET / ETTER_OPPRETTELSE / FOER_FRIST
    paaminnelse_bestilling_spesifikasjon_tid  text,          -- dato eller period, avhengig av type
    paaminnelse_bestilling_utregnet_tid       text
);
revoke select (tekst, grupperingsid) on notifikasjon from public;

create table mottaker_naermeste_leder
(
    sak_id                  uuid references sak (sak_id) on delete cascade,
    notifikasjon_id         uuid references notifikasjon (notifikasjon_id) on delete cascade,
    virksomhetsnummer       text not null,
    virksomhetsnummer_pseud text generated always as (pseudonymize(virksomhetsnummer)) stored,
    fnr_leder               text not null,
    fnr_leder_pseud         text generated always as (pseudonymize(fnr_leder)) stored,
    fnr_ansatt              text not null,
    fnr_ansatt_pseud        text generated always as (pseudonymize(fnr_ansatt)) stored
);
revoke select (virksomhetsnummer, fnr_leder, fnr_ansatt) on mottaker_naermeste_leder from public;

create unique index mottaker_naermeste_leder_unique
on mottaker_naermeste_leder(
    coalesce(notifikasjon_id, '00000000-00000000-00000000-00000000'),
    coalesce(sak_id, '00000000-00000000-00000000-00000000'),
    virksomhetsnummer,
    fnr_leder,
    fnr_ansatt
);

create table mottaker_enkeltrettighet
(
    sak_id          uuid references sak(sak_id) on delete cascade,
    notifikasjon_id uuid references notifikasjon(notifikasjon_id) on delete cascade,
    virksomhetsnummer text not null,
    virksomhetsnummer_pseud text generated always as (pseudonymize(virksomhetsnummer)) stored,
    service_code    text not null,
    service_edition text not null
);
revoke select (virksomhetsnummer) on mottaker_enkeltrettighet from public;

create unique index mottaker_enkeltrettighet_unique
on mottaker_enkeltrettighet(
    coalesce(notifikasjon_id, '00000000-00000000-00000000-00000000'),
    coalesce(sak_id, '00000000-00000000-00000000-00000000'),
    virksomhetsnummer,
    service_code,
    service_edition
);


create table notifikasjon_klikk
(
    hendelse_id           uuid not null primary key,
    notifikasjon_id       uuid not null references notifikasjon(notifikasjon_id) on delete cascade,
    fnr                   text not null,
    fnr_pseud text generated always as (pseudonymize(fnr)) stored,
    klikket_paa_tidspunkt text not null
);
revoke select (fnr) on notifikasjon_klikk from public;

create table ekstern_varsel
(
    varsel_id          uuid not null primary key,
    varsel_type        text not null, -- enum: EPOST / SMS
    notifikasjon_id    uuid not null references notifikasjon(notifikasjon_id) on delete cascade,
    produsent_id       text not null,
    merkelapp          text,
    sendevindu         text not null,
    sendetidspunkt     text null, -- fra bestillingen
    altinn_svar_timestamp text null,
    sms_tekst          text,
    html_tittel        text,
    html_body          text,

    -- enum:
    -- * OppgaveOpprettet.påminnelse
    -- * BeskjedOpprettet.eksterneVarsler
    -- * OppgaveOpprettet.eksterneVarsler
    opprinnelse           text not null,

    -- UTSENDING_IKKE_AVGJORT
    -- +-> UTSENDING_BESTILT
    -- |+-> UTSENDING_VELLYKKET
    -- |+-> UTSENDING_FEILET
    -- +-> INGEN_UTSENDING
    status_utsending  text not null,
    feilkode           text
);

create table ekstern_varsel_mottaker_tlf
(
    varsel_id uuid not null primary key references ekstern_varsel (varsel_id) on delete cascade,
    tlf       text not null
);

create table ekstern_varsel_mottaker_epost
(
    varsel_id uuid not null primary key references ekstern_varsel (varsel_id) on delete cascade,
    epost     text not null
);
