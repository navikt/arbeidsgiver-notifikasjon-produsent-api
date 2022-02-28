create table sak
(
    id                  uuid not null primary key,
    virksomhetsnummer   text not null,
    merkelapp           text        not null,
    grupperingsid       text        not null,
    mottakere           jsonb       not null,
    tittel              text        not null,
    lenke               text        not null,
    tidspunkt_mottatt   timestamptz not null,

    deleted_at          timestamptz null default null,

    constraint grupperingsid_unique unique (merkelapp, grupperingsid)
);

create table sak_id
(
    incoming_sak_id            uuid not null primary key,
    sak_id               uuid not null references sak(id) on delete cascade
);

create table sak_status
(
    id                       uuid not null primary key,
    idempotence_key          text not null,
    sak_id                   uuid not null references sak(id) on delete cascade,
    status                   text not null,
    overstyr_statustekst_med text null,
    tidspunkt_oppgitt        timestamptz null,
    tidspunkt_mottatt        timestamptz not null,

    constraint idempotence_key_unique unique (sak_id, idempotence_key)
);

create view statusoppdateringer_json as
    select
        sak_id,
        json_agg(
            json_build_object(
                'id', id,
                'idempotencyKey', idempotence_key,
                'status', status,
                'overstyrStatustekstMed', overstyr_statustekst_med,
                'tidspunktOppgitt', tidspunkt_oppgitt,
                'tidspunktMottatt', tidspunkt_mottatt
            )
            order by coalesce(tidspunkt_oppgitt, tidspunkt_mottatt)
        ) as statusoppdateringer
    from sak_status
    group by sak_id;

