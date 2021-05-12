create table notifikasjon
(
    koordinat           text primary key,
    uuid                uuid not null,
    merkelapp           text  not null,
    tekst               text  not null,
    grupperingsid       text,
    lenke               text  not null,
    ekstern_id          text  not null,
    mottaker            jsonb not null,
    opprettet_tidspunkt timestamptz
);

create index idxginp on notifikasjon using gin (mottaker jsonb_path_ops);

create index idx_opprettet_tidspunkt on notifikasjon (opprettet_tidspunkt);

create table brukerklikk
(
    fnr                 char(11) not null,
    notifikasjonsid     uuid not null,
    constraint brukerklikk_pk primary key (fnr, notifikasjonsid)
);

create index brukerklikk_fnr_index on brukerklikk(fnr);

create table notifikasjonsid_virksomhet_map
(
    notifikasjonsid     uuid not null primary key,
    virksomhetsnummer   char(9) not null
);
