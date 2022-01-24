alter table notifikasjon alter column virksomhetsnummer set not null;
alter table notifikasjon drop column mottaker;
drop table notifikasjonsid_virksomhet_map;