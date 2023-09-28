alter table registrert_hard_delete_event
    drop constraint registrert_hard_delete_event_aggregate_id_fkey;

create function delete_registrert_hard_delete_event()
    returns trigger
as '
    begin
        delete from registrert_hard_delete_event where aggregate_id = old.aggregate_id;
        return null;
    end;
' language plpgsql;

create trigger trg_delete_registrert_hard_delete_event
    after delete on aggregate
    for each row execute procedure delete_registrert_hard_delete_event();
