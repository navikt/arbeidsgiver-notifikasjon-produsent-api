alter table sak
    add column neste_steg text;

alter table sak
    add column neste_steg_pseud text generated always as (pseudonymize(neste_steg)) stored;

create table sak_oppdatering
(
    hendelse_id uuid primary key,
    sak_id uuid references sak(sak_id) on delete cascade,
    idempotens_key text,

    idempotens_key_pseud text generated always as (pseudonymize(idempotens_key)) stored
);

create index sak_updates_sak_id_idempotence_idx on sak_oppdatering(sak_id, idempotens_key_pseud);