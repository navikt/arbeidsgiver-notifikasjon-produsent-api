create table sak
(
    sak_id                 uuid not null primary key,
    produsent_id           text not null,
    merkelapp              text not null,
    mottaker               text not null,
    opprettet_tidspunkt    timestamp,
    soft_deleted_tidspunkt timestamp
);

