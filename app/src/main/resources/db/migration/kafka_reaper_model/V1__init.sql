
CREATE TABLE notifikasjon_hendelse_relasjon
(
    hendelse_id UUID NOT NULL PRIMARY KEY,
    notifikasjon_id UUID NOT NULL,
    hendelse_type TEXT NOT NULL
);

CREATE TABLE deleted_notifikasjon(
    notifikasjon_id UUID NOT NULL PRIMARY KEY,
    deleted_at timestamptz NOT NULL
);

