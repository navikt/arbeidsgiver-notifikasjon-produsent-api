create type varsel_type as enum ('SMS', 'EPOST');

create table ekstern_varsel_kontaktinfo
(
    varsel_id uuid not null primary key,
    notifikasjon_id uuid not null,
    produsent_id text not null,
    fnr_eller_orgnr text not null,
    sendevindu text not null,
    sendetidspunkt text null,
    varsel_type varsel_type not null,

    tlfnr text
        check (case when varsel_type = 'SMS' then tlfnr is not null else tlfnr is null end),
    sms_tekst text
        check (case when varsel_type = 'SMS' then sms_tekst is not null else sms_tekst is null end),

    epost_adresse text
        check (case when varsel_type = 'EPOST' then epost_adresse is not null else epost_adresse is null end),
    tittel text
        check (case when varsel_type = 'EPOST' then tittel is not null else tittel is null end),
    html_body text
        check (case when varsel_type = 'EPOST' then html_body is not null else html_body is null end),

    tilstand text not null,
    altinn_response jsonb null,

    locked bool not null,
    locked_by text null,
    locked_at timestamp null,
    locked_until timestamp null
);
