alter default privileges in schema public grant all on tables to cloudsqliamuser;
grant all on all tables in schema public to cloudsqliamuser;

create type state as enum ('NY', 'SENDT', 'KVITTERT');
create type channel as enum ('SMS', 'EMAIL');
create type status as enum ('OK', 'FEIL');

create table ekstern_varsel_kontaktinfo
(
    varsel_id uuid not null primary key,
    notifikasjon_id uuid not null,
    produsent_id text not null,
    fnr_eller_orgnr text not null,
    sendevindu text not null,
    sendetidspunkt text null,
    hard_deleted bool not null default false,
    state state not null,

    -- content of message
    varsel_type channel not null,

    tlfnr text check (case when varsel_type = 'SMS' then tlfnr is not null else tlfnr is null end),
    sms_tekst text check (case when varsel_type = 'SMS' then sms_tekst is not null else sms_tekst is null end),

    epost_adresse text check (case when varsel_type = 'EMAIL' then epost_adresse is not null else epost_adresse is null end),
    tittel text check (case when varsel_type = 'EMAIL' then tittel is not null else tittel is null end),
    html_body text check (case when varsel_type = 'EMAIL' then html_body is not null else html_body is null end),

    -- response from delivery service
    sende_status status null default null,
    feilmelding text null default null,
    altinn_response jsonb null default null,
    altinn_feilkode text null default null
);

create table job_queue
(
    id bigserial primary key,
    varsel_id uuid not null,
    locked bool not null,
    locked_by text null, -- only used for debuging
    locked_at timestamp null, -- only used for debugging
    locked_until timestamp null
);

create table emergency_break
(
    id int primary key default 0,
    stop_processing boolean not null,
    detected_at timestamp not null,
    constraint at_most_one_row check (id = 0)
);

-- We've come up with an empty database! That's not normal. Someone
-- should make sure we have consumed everything from the kafka-queue,
-- and then disable the emergency break.
-- Otherwise, we would begin re-sending past notifications, as we
-- get the event with the order information before the event
-- with the result of the order.
insert into emergency_break (stop_processing, detected_at) values (true, CURRENT_TIMESTAMP);
