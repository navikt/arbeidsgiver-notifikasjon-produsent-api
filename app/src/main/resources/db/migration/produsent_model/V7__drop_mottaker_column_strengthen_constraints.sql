alter table notifikasjon drop column mottaker;
alter table notifikasjon alter column virksomhetsnummer set not null;