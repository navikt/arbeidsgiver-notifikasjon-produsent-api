create extension pg_trgm;

create table sak_search
(
    id uuid primary key references sak(id),
    text text not null
);

create index sak_search_text_idx on sak_search using gin (text gin_trgm_ops);
