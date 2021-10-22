create table naermeste_leder_kobling
(
    id                  uuid primary key,
    orgnummer           text not null,
    fnr                 text not null,
    naermeste_leder_fnr text not null
);

create index idx_naermeste_leder_kobling_naermeste_leder_fnr on naermeste_leder_kobling (naermeste_leder_fnr);
