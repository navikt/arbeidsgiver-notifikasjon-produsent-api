-- index for query:
--     DELETE FROM aggregat_hendelse WHERE aggregat_id = $1
create index aggregat_hendelse_aggregat_id_idx on
    aggregat_hendelse (aggregat_id);

-- index for query:
--     DELETE FROM notifikasjon WHERE notifikasjon_id = $1
create index notifikasjon_notifikasojn_id_idx on
    notifikasjon (notifikasjon_id);

-- index for query:
--     DELETE FROM sak WHERE sak_id = $1
create index sak_sak_id_idx on
    sak (sak_id);


-- Index for query:
--     UPDATE ekstern_varsel
--     SET status_utsending = $2
--     WHERE
--           notifikasjon_id = $1
--       AND opprinnelse = $3
--       AND status_utsending = $4
create index ekstern_varsel_notifikasjon_id_idx on
    ekstern_varsel (notifikasjon_id);