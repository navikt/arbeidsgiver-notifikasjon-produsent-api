create materialized view mv_evk_to_delete as
select evk.notifikasjon_id
from ekstern_varsel_kontaktinfo evk
         join hard_delete hd on evk.notifikasjon_id = hd.notifikasjon_id;
