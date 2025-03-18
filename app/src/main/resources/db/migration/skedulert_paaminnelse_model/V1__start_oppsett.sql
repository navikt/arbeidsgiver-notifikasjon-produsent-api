create table notifikasjoner
(
    notifikasjon_id text not null primary key,
    tilstand        text not null,
    merkelapp       text not null,
    grupperingsid   text
);

create index notifikasjoner_koordinat_idx on notifikasjoner (merkelapp, grupperingsid);

create table slettede_saker
(
    merkelapp     text not null,
    grupperingsid text not null,
    primary key (merkelapp, grupperingsid)
);

-- utførte (inkludert avbrutte) bestillinger
create table utforte_bestillinger
(
    bestilling_id text not null primary key
);

create table bestillinger
(
    bestilling_id             text not null primary key,
    paaminnelsestidspunkt     text not null,
    notifikasjon_id           text references notifikasjoner (notifikasjon_id),
    frist_opprettet_tidspunkt text not null,
    frist                     text null,
    start_tidspunkt           text null,
    tidspunkt_json            text not null,
    eksterne_varsler_json     text not null,
    virksomhetsnummer         text not null,
    produsent_id              text not null
);

create index bestillinger_utsendelse_idx on bestillinger (paaminnelsestidspunkt);
create index bestillinger_notifikasjon_id_idx on bestillinger (notifikasjon_id);
