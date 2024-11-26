-- dette er tenkt fikse slik at begge brukere har tilgang til tabeller opprettet uavhengig av hvilken app som kjører migreringen
alter default privileges for user "notifikasjon-bruker-api" in schema public grant all on tables to "bruker-api-kafka-user";
alter default privileges for user "bruker-api-kafka-user" in schema public grant all on tables to "notifikasjon-bruker-api";
alter default privileges for user "notifikasjon-bruker-api" in schema public grant all privileges on sequences to "bruker-api-kafka-user";
alter default privileges for user "bruker-api-kafka-user" in schema public grant all privileges on sequences to "notifikasjon-bruker-api";

-- fikse tilgang på eksisterende tabeller
grant all privileges on schema public to "notifikasjon-bruker-api";
grant all privileges on schema public to "bruker-api-kafka-user";
grant all privileges on schema public to cloudsqliamuser;
grant all privileges on all tables in schema public to "notifikasjon-bruker-api";
grant all privileges on all tables in schema public to "bruker-api-kafka-user";
grant all privileges on all tables in schema public to cloudsqliamuser;