create table mottaker_altinn_ressurs
(
    sak_id                  uuid references sak (sak_id) on delete cascade,
    notifikasjon_id         uuid references notifikasjon (notifikasjon_id) on delete cascade,
    virksomhetsnummer       text not null,
    virksomhetsnummer_pseud text generated always as (pseudonymize(virksomhetsnummer)) stored,
    ressursid               text not null
);
revoke select (virksomhetsnummer) on mottaker_altinn_ressurs from public;

create unique index mottaker_altinn_ressurs_unique
    on mottaker_altinn_ressurs (
                                coalesce(notifikasjon_id, '00000000-00000000-00000000-00000000'),
                                coalesce(sak_id, '00000000-00000000-00000000-00000000'),
                                virksomhetsnummer,
                                ressursid
        );