create index notifikasjon_sortering_tidspunkt_idx
    on notifikasjon (coalesce(paaminnelse_tidspunkt, opprettet_tidspunkt));