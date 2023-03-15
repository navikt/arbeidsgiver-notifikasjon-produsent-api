alter table ekstern_varsel
    add column tjeneste_tittel text;
alter table ekstern_varsel
    add column tjeneste_innhold text;

create table ekstern_varsel_mottaker_tjeneste
(
    varsel_id       uuid not null primary key references ekstern_varsel (varsel_id) on delete cascade,
    tjenestekode    text not null,
    tjenesteversjon text not null
);

create table ekstern_varsel_resultat
(
    varsel_id               uuid not null references ekstern_varsel (varsel_id) on delete cascade,
    resultat_name           text not null,
    resultat_name_pseud     text generated always as (pseudonymize(resultat_name)) stored,
    resultat_receiver       text not null,
    resultat_receiver_pseud text generated always as (pseudonymize(resultat_receiver)) stored,
    resultat_type           text not null,

    constraint resultat_unique unique (varsel_id, resultat_name, resultat_receiver, resultat_type)
);
