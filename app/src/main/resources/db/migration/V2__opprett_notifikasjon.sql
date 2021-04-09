drop table foo;

create table notifikasjon
(
    koordinat           text primary key,
    merkelapp           text  not null,
    tekst               text  not null,
    grupperingsid       text,
    lenke               text  not null,
    ekstern_id          text  not null,
    mottaker            jsonb not null,
    opprettet_tidspunkt timestamptz
);

CREATE INDEX idxginp ON notifikasjon USING GIN (mottaker jsonb_path_ops);
