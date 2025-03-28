grant select on all tables in schema public to sa_notifikasjon_dataprodukt_exporter;

alter table sak
    drop column virksomhetsnummer cascade;