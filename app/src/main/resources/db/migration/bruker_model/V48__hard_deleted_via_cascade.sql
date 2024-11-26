
-- denne tabellen er en koblingstabell mellom sak og notifikasjon
-- det at noe ligger i den betyr ikke at det er slettet, men brukes for å sjekke cascade delete
create table hard_delete_sak_til_notifikasjon_kobling
(
    sak_id       uuid not null,
    notifikasjon_id uuid not null
);


create unique index hard_delete_sak_til_notifikasjon_kobling_uniq
    on hard_delete_sak_til_notifikasjon_kobling (sak_id, notifikasjon_id)