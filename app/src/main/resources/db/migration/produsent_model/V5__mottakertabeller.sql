create table mottaker_altinn_enkeltrettighet
(
    id bigserial primary key,
    notifikasjon_id uuid not null references notifikasjon(id) on delete cascade,
    virksomhet text not null,
    service_code text not null,
    service_edition text not null
);

create table mottaker_digisyfo
(
    id bigserial primary key,
    notifikasjon_id uuid not null references notifikasjon(id) on delete cascade,
    virksomhet text not null,
    fnr_leder text not null,
    fnr_sykmeldt text not null
);