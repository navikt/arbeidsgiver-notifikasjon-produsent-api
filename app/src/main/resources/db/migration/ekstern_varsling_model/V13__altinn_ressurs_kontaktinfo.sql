alter table ekstern_varsel_kontaktinfo
    add column ressursid text;

alter table ekstern_varsel_kontaktinfo
    add ressurs_eposttittel text check (
        case
            when varsel_type = 'ALTINNRESSURS' then ressurs_eposttittel is not null
            else ressurs_eposttittel is null
            end
        );
alter table ekstern_varsel_kontaktinfo
    add ressurs_epostinnhold text check (
        case
            when varsel_type = 'ALTINNRESSURS' then ressurs_epostinnhold is not null
            else ressurs_epostinnhold is null
            end
        );
alter table ekstern_varsel_kontaktinfo
    add ressurs_smsinnhold text check (
        case
            when varsel_type = 'ALTINNRESSURS' then ressurs_smsinnhold is not null
            else ressurs_smsinnhold is null
            end
        );
