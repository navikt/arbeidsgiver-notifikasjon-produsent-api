create table hard_delete
(
    notifikasjon_id uuid primary key,
    inserted_at timestamp without time zone default (now() at time zone 'utc' )
);

create index ekstern_varsel_kontaktinfo_state_idx on
    ekstern_varsel_kontaktinfo(state);