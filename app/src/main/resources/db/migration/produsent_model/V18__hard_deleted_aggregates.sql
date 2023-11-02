create table hard_deleted_aggregates
(
    aggregate_id  uuid not null primary key
);

create table hard_deleted_aggregates_metadata
(
    aggregate_id   uuid not null references hard_deleted_aggregates (aggregate_id) on delete cascade,
    aggregate_type text not null,
    grupperingsid  text,
    merkelapp      text
);

create index on hard_deleted_aggregates_metadata (aggregate_type, grupperingsid, merkelapp);