alter table sak
    add column grupperingsid text null default null;

create index sak_grupperingsid_idx on sak(grupperingsid);
