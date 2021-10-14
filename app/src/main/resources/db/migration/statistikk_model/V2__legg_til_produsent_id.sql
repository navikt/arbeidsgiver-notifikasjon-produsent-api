alter table notifikasjon_statistikk add column produsent_id text;

create index produsent_id_idx on notifikasjon_statistikk (produsent_id);