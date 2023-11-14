alter table notifikasjon_hendelse_relasjon
    add column gruppering_id text null default null,
    add column merkelapp text null default null;