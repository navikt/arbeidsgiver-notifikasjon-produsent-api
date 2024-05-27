alter table sak
    add column neste_steg text;

alter table sak
    add column neste_steg_pseud text generated always as (pseudonymize(neste_steg)) stored;