create table altinn_rolle
(
    role_definition_id    text not null primary key,
    role_definition_code  text not null
);
create index role_definition_code_idx on altinn_rolle (role_definition_code);

