alter table sak alter column opprettet_tidspunkt drop not null;
alter table sak alter column sist_endret_tidspunkt drop not null;

update sak
set opprettet_tidspunkt = null,
    sist_endret_tidspunkt = null;