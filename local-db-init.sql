CREATE ROLE cloudsqliamuser;
create role "bruker-api-kafka-user";
create role "notifikasjon-bruker-api";
create user sa_notifikasjon_dataprodukt_exporter;

CREATE DATABASE "bruker-model";
GRANT ALL PRIVILEGES ON DATABASE "bruker-model" TO postgres;

CREATE DATABASE "produsent-model";
GRANT ALL PRIVILEGES ON DATABASE "produsent-model" TO postgres;

CREATE DATABASE "kafka-reaper-model";
GRANT ALL PRIVILEGES ON DATABASE "kafka-reaper-model" TO postgres;

CREATE DATABASE "ekstern-varsling-model";
GRANT ALL PRIVILEGES ON DATABASE "ekstern-varsling-model" TO postgres;

CREATE DATABASE "skedulert-harddelete-model";
GRANT ALL PRIVILEGES ON DATABASE "skedulert-harddelete-model" TO postgres;

CREATE DATABASE "skedulert-utgatt-model";
GRANT ALL PRIVILEGES ON DATABASE "skedulert-utgatt-model" TO postgres;

CREATE DATABASE "kafka-backup-model";
GRANT ALL PRIVILEGES ON DATABASE "kafka-backup-model" TO postgres;

CREATE DATABASE "dataprodukt-model";
GRANT ALL PRIVILEGES ON DATABASE "dataprodukt-model" TO postgres;
