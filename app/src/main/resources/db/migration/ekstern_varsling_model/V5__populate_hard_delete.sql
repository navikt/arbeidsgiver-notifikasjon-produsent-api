insert into hard_delete (notifikasjon_id)
    (select notifikasjon_id
     from ekstern_varsel_kontaktinfo
     where hard_deleted = true);