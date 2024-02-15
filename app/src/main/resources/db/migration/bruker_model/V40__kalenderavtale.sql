alter table notifikasjon
    add column start_tidspunkt timestamptz;
alter table notifikasjon
    add column slutt_tidspunkt timestamptz;
alter table notifikasjon
    add column lokasjon jsonb;
alter table notifikasjon
    add column digitalt boolean;
