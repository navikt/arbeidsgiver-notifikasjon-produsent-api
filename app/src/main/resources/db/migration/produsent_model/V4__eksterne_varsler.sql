
create type eksternt_varsel_status as enum ('NY', 'SENDT', 'FEILET');

create table eksternt_varsel
(
    varsel_id uuid not null primary key,
    notifikasjon_id uuid not null,
    status eksternt_varsel_status not null,
    feilmelding text
);