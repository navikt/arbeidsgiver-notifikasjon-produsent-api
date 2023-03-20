alter table notifikasjon
    add column lenke_pseud text generated always as (pseudonymize(lenke)) stored;

alter table notifikasjon
    add column ekstern_id_pseud text generated always as (pseudonymize(ekstern_id)) stored;

alter table sak
    add column lenke_pseud text generated always as (pseudonymize(lenke)) stored;

alter table sak_status
    add column ny_lenke_til_sak_pseud text generated always as (pseudonymize(ny_lenke_til_sak)) stored;
