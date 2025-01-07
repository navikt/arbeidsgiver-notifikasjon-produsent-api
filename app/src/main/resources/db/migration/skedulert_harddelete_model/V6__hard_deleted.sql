create table hard_deleted_aggregates
(
    aggregate_id  uuid not null primary key
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
    sak_id       uuid not null,
    notifikasjon_id uuid not null
);


create unique index hard_delete_sak_til_notifikasjon_kobling_uniq
    on hard_delete_sak_til_notifikasjon_kobling (sak_id, notifikasjon_id)