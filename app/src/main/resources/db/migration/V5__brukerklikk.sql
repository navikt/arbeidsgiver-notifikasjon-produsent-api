create table brukerklikk(
   fnr char(11) not null,
   notifikasjonsid text not null,
   constraint brukerklikk_pk primary key (fnr, notifikasjonsid)
);

create index brukerklikk_fnr_index ON brukerklikk(fnr);

create table notifikasjonsid_virksomhet_map(
    notifikasjonsid text not null primary key,
    virksomhetsnummer char(9) not null
);
