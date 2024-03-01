create table notifikasjon_oppdatering
(
    hendelse_id uuid primary key,
    notifikasjon_id uuid references notifikasjon(id) on delete cascade,
    idempotence_key text not null
);

create index notifikasjon_updates_notifikasjon_id_idempotence_idx on notifikasjon_oppdatering(notifikasjon_id, idempotence_key);