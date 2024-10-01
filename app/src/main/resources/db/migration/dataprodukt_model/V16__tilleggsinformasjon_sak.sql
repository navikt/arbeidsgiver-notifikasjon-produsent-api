alter table sak
    add column tilleggsinformasjon text;

alter table sak
    add column tilleggsinformasjon_pseud text generated always as (pseudonymize(tilleggsinformasjon)) stored;