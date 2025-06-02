create table notifikasjoner_sist_lest
(
    fnr char(11) not null primary key,
    tidspunkt timestamptz not null
);