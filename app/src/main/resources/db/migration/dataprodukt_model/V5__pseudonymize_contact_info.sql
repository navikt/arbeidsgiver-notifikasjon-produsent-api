alter table ekstern_varsel_mottaker_tlf
    add column tlf_pseud text generated always as (pseudonymize(tlf)) stored;

alter table ekstern_varsel_mottaker_epost
    add column epost_pseud text generated always as (pseudonymize(epost)) stored;
