create table oppgave
(
    oppgave_id        uuid not null primary key,
    frist             text not null,
    virksomhetsnummer text not null,
    produsent_id      text not null
);

create index oppgave_frist_idx on oppgave (frist);

create table oppgave_sak_kobling
(
    oppgave_id uuid not null primary key,
    sak_id     uuid not null
);

create table kalenderavtale
(
    kalenderavtale_id   uuid not null primary key,
    start_tidspunkt     text not null,
    tilstand            text not null,
    virksomhetsnummer   text not null,
    produsent_id        text not null,
    merkelapp           text not null,
    grupperingsid       text not null,
    opprettet_tidspunkt text not null
);

create index kalenderavtale_starttidspunkt_idx on kalenderavtale (start_tidspunkt);

create table kalenderavtale_sak_kobling
(
    kalenderavtale_id uuid not null primary key,
    sak_id            uuid not null
);

create table skedulert_utgatt
(
    aggregat_id   uuid not null primary key,
    aggregat_type text not null
);


create table hard_deleted_aggregates
(
    aggregate_id uuid not null primary key
);

create table hard_deleted_aggregates_metadata
(
    aggregate_id   uuid not null references hard_deleted_aggregates (aggregate_id) on delete cascade,
    aggregate_type text not null,
    grupperingsid  text,
    merkelapp      text
);

create index on hard_deleted_aggregates_metadata (aggregate_type, grupperingsid, merkelapp);


-- denne tabellen er en koblingstabell mellom sak og notifikasjon
-- det at noe ligger i den betyr ikke at det er slettet, men brukes for å sjekke cascade delete
create table hard_delete_sak_til_notifikasjon_kobling
(
    sak_id          uuid not null,
    notifikasjon_id uuid not null
);


create unique index hard_delete_sak_til_notifikasjon_kobling_uniq
    on hard_delete_sak_til_notifikasjon_kobling (sak_id, notifikasjon_id)