CREATE OR REPLACE FUNCTION pseudonymize(val text)
     RETURNS text
 AS
 $$
 SELECT encode(
                sha256(
                        convert_to(val || '${SALT_VERDI}', 'UTF8')
                ),
                'hex'
        )
 $$
     LANGUAGE sql
     IMMUTABLE;