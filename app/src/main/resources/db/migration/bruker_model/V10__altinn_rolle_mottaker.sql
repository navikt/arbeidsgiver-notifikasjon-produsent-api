
create table mottaker_altinn_rolle
    (
        id bigserial primary key,
        notifikasjon_id uuid not null references notifikasjon(id) on delete cascade,
        virksomhet text not null,
        role_definition_id text not null,
        role_definition_code text not null
    );

create index mottaker_altinn_rolle_idx on
    mottaker_altinn_rolle(virksomhet, role_definition_id, role_definition_code);
