drop index mottaker_altinn_enkeltrettighet_unique;
create unique index mottaker_altinn_enkeltrettighet_unique
on mottaker_altinn_enkeltrettighet (
    coalesce(notifikasjon_id, '00000000-00000000-00000000-00000000'),
    coalesce(sak_id, '00000000-00000000-00000000-00000000'),
    virksomhet,
    service_code,
    service_edition
);

drop index mottaker_digisyfo_unique;
create unique index mottaker_digisyfo_unique
on mottaker_digisyfo (
    coalesce(notifikasjon_id, '00000000-00000000-00000000-00000000'),
    coalesce(sak_id, '00000000-00000000-00000000-00000000'),
    virksomhet,
    fnr_leder,
    fnr_sykmeldt
);

drop index mottaker_altinn_rolle_unique;
create unique index mottaker_altinn_rolle_unique
on mottaker_altinn_rolle (
    coalesce(notifikasjon_id, '00000000-00000000-00000000-00000000'),
    coalesce(sak_id, '00000000-00000000-00000000-00000000'),
    virksomhet,
    role_definition_code,
    role_definition_id
);

drop index mottaker_altinn_reportee_unique;
create unique index mottaker_altinn_reportee_unique
on mottaker_altinn_reportee (
    coalesce(notifikasjon_id, '00000000-00000000-00000000-00000000'),
    coalesce(sak_id, '00000000-00000000-00000000-00000000'),
    virksomhet,
    fnr
);
