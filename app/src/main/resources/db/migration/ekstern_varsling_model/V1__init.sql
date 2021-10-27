alter default privileges in schema public grant all on tables to cloudsqliamuser;
grant all on all tables in schema public to cloudsqliamuser;

create type varsel_type as enum ('SMS', 'EPOST');

create table ekstern_varsel_kontaktinfo
(
    varsel_id uuid not null primary key,
    notifikasjon_id uuid not null,
    produsent_id text not null,
    fnr_eller_orgnr text not null,
    sendevindu text not null,
    sendetidspunkt text null,
    varsel_type varsel_type not null,
    hard_deleted bool not null,

    tlfnr text check (case when varsel_type = 'SMS' then tlfnr is not null else tlfnr is null end),
    sms_tekst text check (case when varsel_type = 'SMS' then sms_tekst is not null else sms_tekst is null end),

    epost_adresse text check (case when varsel_type = 'EPOST' then epost_adresse is not null else epost_adresse is null end),
    tittel text check (case when varsel_type = 'EPOST' then tittel is not null else tittel is null end),
    html_body text check (case when varsel_type = 'EPOST' then html_body is not null else html_body is null end),

    tilstand text not null,
    altinn_response jsonb null
);

create table work_queue
(
    id bigserial primary key,
    varsel_id uuid not null,
    locked bool not null,
    locked_by text null,
    locked_at timestamp null,
    locked_until timestamp null
);

create table work_queue_processing
(
    id int primary key default 0,
    enabled boolean not null,
    constraint at_most_one_row check (id = 0)
);

insert into work_queue_processing (enabled) values (true);
