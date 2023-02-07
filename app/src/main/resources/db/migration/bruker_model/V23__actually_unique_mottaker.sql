drop index mottaker_altinn_enkeltrettighet_unique;
create unique index mottaker_altinn_enkeltrettighet_unique
on mottaker_altinn_enkeltrettighet (
    notifikasjon_id,
    sak_id,
    virksomhet,
    service_code,
    service_edition
);

drop index mottaker_digisyfo_unique;
create unique index mottaker_digisyfo_unique
on mottaker_digisyfo (
    notifikasjon_id,
    sak_id,
    virksomhet,
    fnr_leder,
    fnr_sykmeldt
);

drop index mottaker_altinn_rolle_unique;
create unique index mottaker_altinn_rolle_unique
on mottaker_altinn_rolle (
    notifikasjon_id,
    sak_id,
    virksomhet,
    role_definition_code,
    role_definition_id
);

drop index mottaker_altinn_reportee_unique;
create unique index mottaker_altinn_reportee_unique
on mottaker_altinn_reportee (
    notifikasjon_id,
    sak_id,
    virksomhet,
    fnr
);
