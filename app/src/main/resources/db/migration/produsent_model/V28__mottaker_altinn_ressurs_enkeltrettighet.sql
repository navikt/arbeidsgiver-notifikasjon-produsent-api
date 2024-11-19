create table mottaker_altinn_ressurs
(
    id              bigserial primary key,
    notifikasjon_id uuid not null references notifikasjon on delete cascade,
    virksomhet      text not null,
    ressursId       text not null
);

create index mottaker_altinn_ressurs_notifikasjon_id_idx
    on mottaker_altinn_ressurs (notifikasjon_id);

create unique index mottaker_altinn_ressurs_unique
    on mottaker_altinn_ressurs (notifikasjon_id, virksomhet, ressursId);

create view mottakere_altinn_ressurs_json as
select notifikasjon_id,
       json_agg(
               json_build_object(
                       '@type', 'altinnRessurs',
                       'virksomhetsnummer', virksomhet,
                       'ressursId', ressursId
               )
       ) as mottakere
from mottaker_altinn_ressurs
group by notifikasjon_id;