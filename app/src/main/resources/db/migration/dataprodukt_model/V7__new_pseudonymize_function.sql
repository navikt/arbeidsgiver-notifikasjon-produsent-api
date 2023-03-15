create or replace function pseudonymize(val text)
    returns text
as
$$
select encode(sha256((val || (select * from salt))::bytea), 'hex')
$$
    language sql
    immutable;
