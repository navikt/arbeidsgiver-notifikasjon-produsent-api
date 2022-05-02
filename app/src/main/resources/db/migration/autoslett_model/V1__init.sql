alter
default privileges in schema public grant all on tables to cloudsqliamuser;
grant all
on all tables in schema public to cloudsqliamuser;

create table skedulert_hard_delete
(
    aggregate_id             uuid      not null primary key,
    aggregate_type           text      not null,
    virksomhetsnummer        text      not null,
    produsentid              text      not null,
    merkelapp                text      not null,
    beregnet_slettetidspunkt timestamp not null,
    input_base               timestamp not null,
    input_om                 text null,
    input_den                text null
);

create index beregnet_slettetidspunkt_idx on skedulert_hard_delete (beregnet_slettetidspunkt);
