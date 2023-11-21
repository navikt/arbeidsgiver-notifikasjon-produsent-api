alter table sak_status rename column tidspunkt to mottatt_tidspunkt;
alter table sak_status add column oppgitt_tidspunkt timestamp with time zone;