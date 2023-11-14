create or replace function pseudonymize(val text)
    returns text
as
$$
select encode(sha256(convert_to(val || (select * from salt), 'UTF8')), 'hex')
$$
    language sql
    immutable;