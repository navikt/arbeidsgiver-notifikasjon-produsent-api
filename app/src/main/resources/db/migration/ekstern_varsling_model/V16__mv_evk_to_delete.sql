create materialized view ekstern_varsel_kontaktinfo_to_delete as
select evk.notifikasjon_id
from ekstern_varsel_kontaktinfo evk
         join hard_delete hd on evk.notifikasjon_id = hd.notifikasjon_id
where evk.state = 'KVITTERT';

-- required for refresh ... concurrently
create unique index idx_evk_to_delete_notifikasjon_id
    on ekstern_varsel_kontaktinfo_to_delete (notifikasjon_id);
