create table varsel_statistikk_bestilling
(
    varsel_id       uuid not null primary key,
    varsel_type     text not null,
    notifikasjon_id uuid not null,
    produsent_id    text not null,
    mottaker        text not null
);

create index notifikasjon_id_varsel_statistikk_bestilling_idx on varsel_statistikk_bestilling (notifikasjon_id);
create index produsent_id_varsel_statistikk_bestilling_idx on varsel_statistikk_bestilling (produsent_id);
create index varsel_type_varsel_statistikk_bestilling_idx on varsel_statistikk_bestilling (varsel_type);