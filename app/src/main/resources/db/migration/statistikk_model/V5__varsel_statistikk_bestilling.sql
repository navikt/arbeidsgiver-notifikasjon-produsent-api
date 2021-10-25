create table varsel_bestilling
(
    varsel_id       uuid not null primary key,
    varsel_type     text not null,
    notifikasjon_id uuid not null,
    produsent_id    text not null,
    mottaker        text not null
);

create index notifikasjon_id_varsel_bestilling_idx on varsel_bestilling (notifikasjon_id);
create index produsent_id_varsel_bestilling_idx on varsel_bestilling (produsent_id);
create index varsel_type_varsel_bestilling_idx on varsel_bestilling (varsel_type);

-- noinspection SqlResolve
drop table varsel_statistikk;

create table varsel_resultat
(
    hendelse_id     uuid not null primary key,
    notifikasjon_id uuid not null,
    varsel_id       uuid not null,
    produsent_id    text not null,
    status          text not null
);

create index notifikasjon_id_varsel_idx on varsel_resultat (notifikasjon_id);
create index produsent_id_varsel_idx on varsel_resultat (produsent_id);
create index varsel_id_varsel_idx on varsel_resultat (varsel_id);

-- noinspection SqlWithoutWhere
delete from notifikasjon_statistikk;
-- noinspection SqlWithoutWhere
delete from notifikasjon_statistikk_klikk;

alter table notifikasjon_statistikk rename to notifikasjon;
alter table notifikasjon_statistikk_klikk rename to notifikasjon_klikk;

