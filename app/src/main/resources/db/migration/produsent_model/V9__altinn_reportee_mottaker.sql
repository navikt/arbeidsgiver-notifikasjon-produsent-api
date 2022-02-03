create table mottaker_altinn_reportee
(
    id bigserial primary key,
    notifikasjon_id uuid not null references notifikasjon(id) on delete cascade,
    virksomhet text not null,
    fnr text not null
);

create view mottakere_altinn_reportee_json as
select
    notifikasjon_id,
    json_agg(
            json_build_object(
                    '@type', 'altinnReportee',
                    'virksomhetsnummer', virksomhet,
                    'fnr', fnr
                )
        ) as mottakere
from mottaker_altinn_reportee
group by notifikasjon_id;
