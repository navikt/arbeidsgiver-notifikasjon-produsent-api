alter table aggregate add column grupperingsid text;
create table registrert_hard_delete_event
(
    aggregate_id      uuid not null primary key references aggregate (aggregate_id) on delete cascade,
    hendelse_id       uuid not null,
    deleted_at        text not null
);