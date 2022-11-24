
create table paaminnelse_eksternt_varsel
(
    varsel_id uuid not null primary key,
    notifikasjon_id uuid not null,
    status eksternt_varsel_status not null,
    feilmelding text
);

create view paaminnelse_eksterne_varsler_json as
select
    notifikasjon_id,
    json_agg(
            json_build_object(
                    'varselId', varsel_id,
                    'status', status,
                    'feilmelding', feilmelding
                )
        ) as paaminnelse_eksterne_varsler_json
from paaminnelse_eksternt_varsel
group by notifikasjon_id;

