create table sak
(
    id uuid not null primary key,
    virksomhetsnummer text not null,
    tittel text not null,
    lenke text not null,
    merkelapp text not null
);

create table sak_status
(
    id uuid not null primary key,
    sak_id uuid not null references sak(id) on delete cascade,
    status text not null,
    overstyrt_statustekst text,
    tidspunkt timestamptz not null
);

create view sak_status_json as
select
    sak_id,
    json_agg(
            json_build_object(
                    'sakStatusId', id,
                    'status', status,
                    'overstyrtStatustekst', overstyrt_statustekst,
                    'tidspunkt', tidspunkt
                )
        ) as statuser
from sak_status
group by sak_id;


alter table mottaker_digisyfo
    alter column notifikasjon_id drop not null;
alter table mottaker_altinn_reportee
    alter column notifikasjon_id drop not null;
alter table mottaker_altinn_rolle
    alter column notifikasjon_id drop not null;
alter table mottaker_altinn_enkeltrettighet
    alter column notifikasjon_id drop not null;

alter table mottaker_digisyfo
    add column sak_id uuid null references sak(id) on delete cascade;
alter table mottaker_altinn_reportee
    add column sak_id uuid null references sak(id) on delete cascade;
alter table mottaker_altinn_rolle
    add column sak_id uuid null references sak(id) on delete cascade;
alter table mottaker_altinn_enkeltrettighet
    add column sak_id uuid null references sak(id) on delete cascade;

drop view notifikasjoner_for_digisyfo_fnr;
create view mottaker_digisyfo_for_fnr as
    select
        m.fnr_leder as fnr_leder,
        m.notifikasjon_id as notifikasjon, -- delete this when migrated
        m.notifikasjon_id as notifikasjon_id,
        m.sak_id as sak_id,
        m.virksomhet as virksomhet
    from mottaker_digisyfo m
    join naermeste_leder_kobling k on
        m.fnr_leder = k.naermeste_leder_fnr
        and m.fnr_sykmeldt = k.fnr
        and m.virksomhet = k.orgnummer;

