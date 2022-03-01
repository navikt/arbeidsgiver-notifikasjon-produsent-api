alter table mottaker_digisyfo
    drop constraint mottaker_digisyfo_notifikasjon_id_fkey;
alter table mottaker_altinn_reportee
    drop constraint mottaker_altinn_reportee_notifikasjon_id_fkey;
alter table mottaker_altinn_rolle
    drop constraint mottaker_altinn_rolle_notifikasjon_id_fkey;
alter table mottaker_altinn_enkeltrettighet
    drop constraint mottaker_altinn_enkeltrettighet_notifikasjon_id_fkey;

alter table mottaker_digisyfo
    rename column notifikasjon_id to aggregate_id;
alter table mottaker_altinn_reportee
    rename column notifikasjon_id to aggregate_id;
alter table mottaker_altinn_rolle
    rename column notifikasjon_id to aggregate_id;
alter table mottaker_altinn_enkeltrettighet
    rename column notifikasjon_id to aggregate_id;

drop view notifikasjoner_for_digisyfo_fnr;
create view aggregate_id_for_digisyfo_fnr as
select m.fnr_leder as fnr_leder, m.aggregate_id as aggregate_id
from mottaker_digisyfo m
         join naermeste_leder_kobling k on
            m.fnr_leder = k.naermeste_leder_fnr
        and m.fnr_sykmeldt = k.fnr
        and m.virksomhet = k.orgnummer;


-- TODO: det over må ut i egen migrering og deployes stegvis

create table sak
(
    sak_id uuid not null primary key,
    virksomhetsnummer text not null,
    tittel text not null,
    lenke text not null,
    merkelapp text not null
);

create table sak_status
(
    sak_status_id uuid not null primary key,
    sak_id uuid not null references sak(sak_id) on delete cascade,
    status text not null,
    overstyrt_statustekst text,
    tidspunkt timestamptz not null
);

create view sak_status_json as
select
    sak_id,
    json_agg(
        json_build_object(
            'sakStatusId', sak_status_id,
            'status', status,
            'overstyrtStatustekst', overstyrt_statustekst,
            'tidspunkt', tidspunkt
        )
    ) as statuser
from sak_status
group by sak_id;
