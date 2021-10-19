create table sms_varsel_kontaktinfo
(
    varsel_id uuid not null primary key,
    notifikasjon_id uuid not null,
    produsent_id text not null,
    tlfnr text not null,
    fnr_eller_orgnr text not null,
    smsTekst text not null,
    sendevindu text not null,
    sendetidspunkt text not null,

    tilstand text not null,
    altinn_response jsonb null,

    locked_by text null,
    locked_at timestamp null,
    locked_until timestamp null
);

create table epost_varsel_kontaktinfo
(
    varsel_id uuid not null primary key,
    notifikasjon_id uuid not null,
    produsent_id text not null,
    epost_adresse text not null,
    fnr_eller_orgnr text not null,
    tittel text not null,
    html_body text not null,
    sendevindu text not null,
    sendetidspunkt text not null,

    tilstand text not null,
    altinn_response jsonb null,

    locked_by text null,
    locked_at timestamp null,
    locked_until timestamp null
);
