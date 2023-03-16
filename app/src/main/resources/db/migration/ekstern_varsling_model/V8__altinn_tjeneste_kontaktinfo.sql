
alter table ekstern_varsel_kontaktinfo
    add column service_code text;

alter table ekstern_varsel_kontaktinfo
    add column service_edition text;

alter table ekstern_varsel_kontaktinfo
    add tjeneste_tittel text check (
        case
            when varsel_type = 'ALTINNTJENESTE' then tjeneste_tittel is not null
            else tjeneste_tittel is null
            end
        );

alter table ekstern_varsel_kontaktinfo
    add tjeneste_innhold text check (
        case
            when varsel_type = 'ALTINNTJENESTE' then tjeneste_innhold is not null
            else tjeneste_innhold is null
            end
        );