
create table mottaker_altinn_enkeltrettighet
    (
        id bigserial primary key,
        notifikasjon_id uuid not null references notifikasjon(id) on delete cascade,
        virksomhet text not null,
        service_code text not null,
        service_edition text not null
    );

create index mottaker_altinn_enkeltrettighet_idx on
    mottaker_altinn_enkeltrettighet(virksomhet, service_code, service_edition);

create table mottaker_digisyfo
    (
        id bigserial primary key,
        notifikasjon_id uuid not null references notifikasjon(id) on delete cascade,
        virksomhet text not null,
        fnr_leder text not null,
        fnr_sykmeldt text not null
    );

create index mottaker_digisyfo_idx on mottaker_digisyfo(fnr_leder);

create view notifikasjoner_for_digisyfo_fnr as
    select m.fnr_leder as fnr_leder, m.notifikasjon_id as notifikasjon_id
    from mottaker_digisyfo m
    join naermeste_leder_kobling k on
        m.fnr_leder = k.naermeste_leder_fnr
        and m.fnr_sykmeldt = k.fnr
        and m.virksomhet = k.orgnummer;
