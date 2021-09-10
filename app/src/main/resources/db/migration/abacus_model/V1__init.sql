CREATE TABLE notifikasjon_statistikk
(
    id           serial,
    hendelseId   uuid,
    hendelseType text not null,
    merkelapp    text not null,
    mottaker     text not null,
    checksum     text,
    created      timestamptz,
    updated      timestamptz,
    deleted      timestamptz
);

CREATE INDEX type_idx ON notifikasjon_statistikk (hendelseType);
CREATE INDEX merkelapp_idx ON notifikasjon_statistikk (merkelapp);
CREATE INDEX mottaker_idx ON notifikasjon_statistikk (mottaker);
CREATE INDEX checksum_idx ON notifikasjon_statistikk (checksum);

