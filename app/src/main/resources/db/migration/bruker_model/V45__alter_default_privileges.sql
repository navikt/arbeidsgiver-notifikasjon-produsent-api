alter default privileges in schema public grant all on tables to "bruker-api-kafka-user";
alter default privileges in schema public grant all on tables to "notifikasjon-bruker-api";

alter default privileges in schema public grant all privileges on sequences to "bruker-api-kafka-user";
alter default privileges in schema public grant all privileges on sequences to "notifikasjon-bruker-api";
