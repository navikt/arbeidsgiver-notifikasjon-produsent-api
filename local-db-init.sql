CREATE ROLE cloudsqliamuser;

CREATE DATABASE "bruker-model";
GRANT ALL PRIVILEGES ON DATABASE "bruker-model" TO postgres;

CREATE DATABASE "produsent-model";
GRANT ALL PRIVILEGES ON DATABASE "produsent-model" TO postgres;

CREATE DATABASE "kafka-reaper-model";
GRANT ALL PRIVILEGES ON DATABASE "kafka-reaper-model" TO postgres;

CREATE DATABASE "statistikk-model";
GRANT ALL PRIVILEGES ON DATABASE "statistikk-model" TO postgres;

CREATE DATABASE "ekstern-varsling-model";
GRANT ALL PRIVILEGES ON DATABASE "ekstern-varsling-model" TO postgres;
