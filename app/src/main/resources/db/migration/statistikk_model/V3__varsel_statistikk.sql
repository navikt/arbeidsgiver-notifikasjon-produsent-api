create table varsel_statistikk
(
    hendelse_id            uuid not null primary key,
    notifikasjon_id        uuid not null,
    produsent_id           text not null,
    status                 text not null
);

create index notifikasjon_id_varsel_idx on varsel_statistikk (notifikasjon_id);
create index produsent_id_varsel_idx on varsel_statistikk (produsent_id);