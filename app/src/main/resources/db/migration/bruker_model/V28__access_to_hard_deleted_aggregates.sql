alter default privileges for user "notifikasjon-bruker-api" in schema public grant all on tables to cloudsqliamuser;
alter default privileges for user "notifikasjon-bruker-api" in schema public grant all on tables to "bruker-api-kafka-user";

alter default privileges for user "bruker-api-kafka-user" in schema public grant all on tables to cloudsqliamuser;
alter default privileges for user "bruker-api-kafka-user" in schema public grant all on tables to "notifikasjon-bruker-api";

grant all privileges on schema public to "notifikasjon-bruker-api";
grant all privileges on schema public to "bruker-api-kafka-user";
grant all privileges on schema public to cloudsqliamuser;
