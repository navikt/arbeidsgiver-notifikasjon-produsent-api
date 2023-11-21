alter table eksternt_varsel add column kilde_hendelse jsonb;
alter table paaminnelse_eksternt_varsel add column kilde_hendelse jsonb;
-- make kilde_hendelse not null after rebuilding model

create or replace view eksterne_varsler_json as
select
    notifikasjon_id,
    json_agg(
            json_build_object(
                    'varselId', varsel_id,
                    'status', status,
                    'feilmelding', feilmelding,
                    'kildeHendelse', kilde_hendelse
            )
    ) as eksterne_varsler_json
from eksternt_varsel
group by notifikasjon_id;

create or replace view paaminnelse_eksterne_varsler_json as
select
    notifikasjon_id,
    json_agg(
            json_build_object(
                    'varselId', varsel_id,
                    'status', status,
                    'feilmelding', feilmelding,
                    'kildeHendelse', kilde_hendelse
            )
    ) as paaminnelse_eksterne_varsler_json
from paaminnelse_eksternt_varsel
group by notifikasjon_id;
