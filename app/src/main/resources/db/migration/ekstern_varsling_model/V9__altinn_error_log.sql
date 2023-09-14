create table altinn_response_log
(
    id bigserial primary key,
    varsel_id uuid not null,
    timestamp text not null,
    altinn_feilkode text,
    altinn_response jsonb,
    exception text
);