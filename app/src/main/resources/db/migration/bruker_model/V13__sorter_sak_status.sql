drop view sak_status_json;
create view sak_status_json as
select
    sak_id,
    json_agg(
            json_build_object(
                    'sakStatusId', id,
                    'status', status,
                    'overstyrtStatustekst', overstyrt_statustekst,
                    'tidspunkt', tidspunkt
                )
        order by tidspunkt desc) as statuser,
    max(tidspunkt) as sist_endret
from sak_status
group by sak_id;

