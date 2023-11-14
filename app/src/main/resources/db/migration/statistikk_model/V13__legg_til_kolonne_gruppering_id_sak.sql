alter table sak
    add column gruppering_id text null default null,
    add column hard_deleted_tidspunkt timestamp null default null;