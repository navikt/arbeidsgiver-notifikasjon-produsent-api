alter table notifikasjon_hendelse_relasjon
    add column gruppering_id uuid null default null,
    add column merkelapp text null default null;