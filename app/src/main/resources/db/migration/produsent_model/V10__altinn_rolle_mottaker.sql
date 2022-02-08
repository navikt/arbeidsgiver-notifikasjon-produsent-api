create table mottaker_altinn_rolle
(
    id bigserial primary key,
    notifikasjon_id uuid not null references notifikasjon(id) on delete cascade,
    virksomhet text not null,
    role_definition_code text not null,
    role_definition_id text not null
);

create view mottaker_altinn_rolle_json as
select
    notifikasjon_id,
    json_agg(
            json_build_object(
                    '@type', 'altinnRolle',
                    'virksomhetsnummer', virksomhet,
                    'roleDefinitionCode', role_definition_code,
                    'roleDefinitionId', role_definition_id
                )
        ) as mottakere
from mottaker_altinn_rolle
group by notifikasjon_id;