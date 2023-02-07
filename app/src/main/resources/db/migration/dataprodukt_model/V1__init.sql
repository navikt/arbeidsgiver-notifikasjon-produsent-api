alter default privileges in schema public grant all on tables to cloudsqliamuser;
grant all on all tables in schema public to cloudsqliamuser;

create table sak
(
    sak_id                 uuid not null primary key,
    produsent_id           text not null,
    merkelapp              text not null,
    mottaker               text not null,
    opprettet_tidspunkt    timestamp,
    soft_deleted_tidspunkt timestamp
);

create table notifikasjon
(
    notifikasjon_id        uuid not null primary key,
    notifikasjon_type      text not null,
    merkelapp              text not null,
    mottaker               text not null,
    checksum               text,
    opprettet_tidspunkt    timestamp,
    utfoert_tidspunkt      timestamp,
    soft_deleted_tidspunkt timestamp,
    utgaatt_tidspunkt timestamp,
    frist date null default null,
    produsent_id text
);

create table notifikasjon_klikk
(
    hendelse_id           uuid not null primary key,
    notifikasjon_id       uuid not null,
    klikket_paa_tidspunkt timestamp
);

create table ekstern_varsel
(
    varsel_id       uuid not null primary key,
    varsel_type     text not null, -- enum: EPOST / SMS
    notifikasjon_id uuid not null,
    produsent_id    text not null,
    mottaker        text not null, -- sms/epost kontaktinfo?
    tekst           text,
    merkelapp       text,
    status          text not null, -- enum: BESTILT / VELLYKKET / FEILET
    feilkode        text
);

-- Skal vi ha mottaker_sms mottaker_epost mottaker_servicecode?