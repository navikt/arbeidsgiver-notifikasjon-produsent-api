CREATE TABLE notifikasjon
(
    id                  uuid  not null primary key,
    mottaker            jsonb not null,
    merkelapp           text  not null,
    tekst               text  not null,
    lenke               text  not null,
    ekstern_id          text  not null,
    opprettet_tidspunkt timestamptz,
    grupperingsid       text  null,
    type                text  not null,
    tilstand            text  not null,

    constraint unikt_koordinat unique (merkelapp, ekstern_id)
);

CREATE INDEX merkelapp_idx ON notifikasjon (merkelapp);
