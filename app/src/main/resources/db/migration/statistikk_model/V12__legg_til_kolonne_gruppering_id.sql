alter table notifikasjon
    add column gruppering_id text,
    add column hard_deleted_tidspunkt timestamp;
