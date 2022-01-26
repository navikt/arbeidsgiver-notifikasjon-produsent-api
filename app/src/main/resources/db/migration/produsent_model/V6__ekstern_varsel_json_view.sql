alter table notifikasjon alter column mottaker drop not null;

create view eksterne_varsler_json as
select
    notifikasjon_id,
    json_agg(
            json_build_object(
                    'varselId', varsel_id,
                    'status', status,
                    'feilmelding', feilmelding
                )
        ) as eksterne_varsler_json
from eksternt_varsel
group by notifikasjon_id;


create view mottakere_altinn_enkeltrettighet_json as
select
    notifikasjon_id,
    json_agg(
            json_build_object(
                    '@type', 'altinn',
                    'virksomhetsnummer', virksomhet,
                    'serviceCode', service_code,
                    'serviceEdition', service_edition
                )
        ) as mottakere
from mottaker_altinn_enkeltrettighet
group by notifikasjon_id;

create view mottakere_digisyfo_json as
select
    notifikasjon_id,
    json_agg(
            json_build_object(
                    '@type', 'naermesteLeder',
                    'virksomhetsnummer', virksomhet,
                    'naermesteLederFnr', fnr_leder,
                    'ansattFnr', fnr_sykmeldt
                )
        ) as mottakere
from mottaker_digisyfo
group by notifikasjon_id;
