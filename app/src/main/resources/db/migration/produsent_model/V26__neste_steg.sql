ALTER TABLE sak ADD COLUMN neste_steg TEXT;

create table sak_oppdatering
(
    hendelse_id uuid primary key,
    sak_id uuid references sak(id) on delete cascade,
    idempotence_key text not null
);

create index sak_updates_sak_id_idempotence_idx on sak_oppdatering(sak_id, idempotence_key);