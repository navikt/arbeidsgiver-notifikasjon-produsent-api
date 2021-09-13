create table notifikasjon_statistikk
(
    notifikasjon_id        uuid not null primary key,
    hendelse_type          text not null,
    merkelapp              text not null,
    mottaker               text not null,
    checksum               text,
    opprettet_tidspunkt    timestamp,
    utfoert_tidspunkt      timestamp,
    soft_deleted_tidspunkt timestamp
);

create table notifikasjon_statistikk_klikk
(
    hendelse_id           uuid not null primary key,
    notifikasjon_id       uuid not null,
    klikket_paa_tidspunkt timestamp
);

create index type_idx on notifikasjon_statistikk (hendelse_type);
create index merkelapp_idx on notifikasjon_statistikk (merkelapp);
create index mottaker_idx on notifikasjon_statistikk (mottaker);
create index checksum_idx on notifikasjon_statistikk (checksum);
