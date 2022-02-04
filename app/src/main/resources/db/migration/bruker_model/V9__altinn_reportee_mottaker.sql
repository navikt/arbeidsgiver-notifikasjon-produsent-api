
create table mottaker_altinn_reportee
    (
        id bigserial primary key,
        notifikasjon_id uuid not null references notifikasjon(id) on delete cascade,
        virksomhet text not null,
        fnr text not null
    );

create index mottaker_altinn_reportee_idx on
    mottaker_altinn_reportee(virksomhet, fnr);
