alter default privileges in schema public grant all on tables to cloudsqliamuser;
grant all on all tables in schema public to cloudsqliamuser;

create table topic_notifikasjon
(
    id bigserial primary key,
    partition int not null,
    "offset" bigint not null,
    timestamp bigint not null,
    timestamp_type int not null,
    headers jsonb not null, /** List<Pair<String, String>>. duplicate "keys" allowed. value is base64-encoded. */
    event_key bytea null,
    event_value bytea null
);

create index topic_notifikasjon_event_key_idx on topic_notifikasjon(event_key);
