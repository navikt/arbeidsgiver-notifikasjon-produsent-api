create table mottaker_altinn_tilgang
(
    id              bigserial primary key,
    virksomhet      text not null,
    altinn_tilgang  text not null,
    notifikasjon_id uuid
        references notifikasjon
            on delete cascade,
    sak_id          uuid
        references sak
            on delete cascade
);

create index mottaker_altinn_tilgang_idx
    on mottaker_altinn_tilgang (virksomhet, altinn_tilgang);

create unique index mottaker_altinn_tilgang_unique
    on mottaker_altinn_tilgang (COALESCE(notifikasjon_id, '00000000-0000-0000-0000-000000000000'::uuid),
                                COALESCE(sak_id, '00000000-0000-0000-0000-000000000000'::uuid), virksomhet,
                                altinn_tilgang);