# produsent-api

# Lokal utvikling

For lokal utvikling benyttes docker-compose til å starte nødvendige platform tjenester (postgres, kafka etc).
Lokal app startes ved å kjøre main metoden i LocalMain.kt.

Apiene blir da å nå på:
* http://ag-notifikasjon-produsent-api.localhost:8081/api/graphql
* http://ag-notifikasjon-bruker-api.localhost:8081/api/graphql

Hvert api kan inspiseres vha hostet graphql ide:
* http://ag-notifikasjon-produsent-api.localhost:8081/ide
* http://ag-notifikasjon-bruker-api.localhost:8081/ide

For at dette skal virke må man sende med riktig auth header (Authorization: Bearer <jwt>). 
Du kan f.eks. sette opp mod-header plugin i chrome eller firefox.
Bruk LocalhostIssuer til å lage tokens du kan bruke i header.

## Deploy med reset og rebuild 
obs: kun for ikke kritiske apps som f.eks statistikk.
Scale ned replicaset: 
`kubectl get replicaset`
`kubectl scale --replicas=0 deployment/notifikasjon-statistikk`
deploy ny app:
- med migrering som nullstiller database til ønsket tidspunkt eller helt.
- med ny consumer-group-id postfix eller MigrationOps som setter bestemt offset.

## Koble direkte til DEV databaser

Du må ha være autentisert med `gcloud` og ha `cloud_sql_proxy` installert.

Kjør `./cloudsql-connect.sh dev notifikasjon-bruker-api 5432` og postgres-databasen vil være tilgjengelig på 
localhost:5432. Brukernavn er e-post-adressen din. Passord får du ved å kjøre `gcloud auth print-access-token`.

## Koble direkte til en topic på aiven

Dersom man trenger å administrere en topic (f.eks. inspisere en topic, eller endre offsets for en consumer group),
så kan man benytte [kafka cli](https://kafka.apache.org/quickstart) kombinert med [nais aiven cli](https://doc.nais.io/cli/commands/aiven/#aiven-command)
kafka-cli kan man laste ned med `./download_kafka_cli.sh` nais aiven [installeres](https://doc.nais.io/cli/install/) med feks homebrew.

Det er opprettet en `notifikasjon-devops` applikasjon med read access til topic i dev og prod.
Etter nais aiven er installert kan man opprette applikasjonen. husk å ha valgt riktig context i k8s først:
`nais aiven create notifikasjon-devops fager`


Deretter kan man generere credentials som kan brukes med f.eks kafka-cli:
`nais aiven get fager-notifikasjon-devops-ad9c851a fager`

Dette genererer noen filer med secrets i en mappe lokalt, og viser hvor disse ble laget. 
Man kan med disse credentials bruke f.eks. kafka-console-consumer eler kafka-consumer-groups.

Noen eksempler på bruk:

Set env vars fra genrerert secrets (som f.eks $KAFKA_BROKERS)
```
source /var/folders/7d/d3gk6jrx4c31pbjqb3qcyc_r0000gn/T/aiven-secret-1881600168/kafka-secret.env
```

List offsets for en consumer group:
```
./kafka-consumer-groups.sh --bootstrap-server $KAFKA_BROKERS --command-config /var/folders/7d/d3gk6jrx4c31pbjqb3qcyc_r0000gn/T/aiven-secret-1881600168/kafka.properties --group bruker-model-builder --describe
```

Dersom man skal endre en offset på en partisjon må consumer group være inaktiv. Dette gjøres enklest ved å skalere ned deployment til 0:
```
kubectl scale --replicas=0 deployment/notifikasjon-bruker-api
```

Hopp over en offset på en gitt partisjon for en consumer group:
```
./kafka-consumer-groups.sh --bootstrap-server $KAFKA_BROKERS --command-config /var/folders/7d/d3gk6jrx4c31pbjqb3qcyc_r0000gn/T/aiven-secret-1881600168/kafka.properties --group bruker-model-builder --topic fager.notifikasjon:12 --reset-offsets --shift-by 1 --execute
```

Sett til et bestemt offset på en gitt partisjon for en consumer group:
```
kafka-consumer-groups.sh --bootstrap-server $KAFKA_BROKERS --command-config /var/folders/7d/d3gk6jrx4c31pbjqb3qcyc_r0000gn/T/aiven-secret-1881600168/kafka.properties --group bruker-model-builder --topic fager.notifikasjon:12 --reset-offsets --to-offset 50 --execute
```

## Ticks n' Trips

* start lokal kafka cluster
  * `docker-compose up -d`
* connect til broker
  * `docker exec -it broker bash`
* kjøre command på broker
  * `docker exec -it broker <command>`
* vis broker version
  * `kafka-broker-api-versions --bootstrap-server localhost:9092 --version`
* create topic
  * `kafka-topics --zookeeper $ZK_HOSTS --create --topic fager.notifikasjon --partitions 3 --replication-factor 3`
    * legg til `--if-not-exists` for å kun opprette dersom den ikke eksisterer fra før
* list topics
  * `kafka-topics --zookeeper zookeeper:2181 --list` 
  * `kafka-topics --zookeeper zookeeper:2181 --list --exclude-internal`  
* describe topic
  * `kafka-topics --zookeeper zookeeper:2181 --topic fager.notifikasjon --describe`
* juster opp antall partisjoner for topic
  * `kafka-topics --zookeeper zookeeper:2181 --alter --topic fager.notifikasjon --partitions 5`
* purge en topic (krever å sette retention lav, så vente, så slette retention)
  * `kafka-configs --zookeeper zookeeper:2181 --alter --entity-type topics --entity-name fager.notifikasjon --add-config retention.ms=1000`
  * _a few moments later_
  * `kafka-configs --zookeeper zookeeper:2181 --alter --entity-type topics --entity-name fager.notifikasjon --delete-config retention.ms`
* list config for en topic
  * `kafka-configs --bootstrap-server localhost:9092 --describe --entity-type topics --entity-name fager.notifikasjon`
* endre config på en topic
  * `kafka-configs --bootstrap-server localhost:9092 --alter --entity-type topics --entity-name fager.notifikasjon --add-config cleanup.policy=compact`
* delete topic
  * `kafka-topics --zookeeper zookeeper:2181 --delete --topic fager.notifikasjon`
* consume en topic og print til console (default from latest)
  * `kafka-console-consumer --bootstrap-server localhost:9092 --topic fager.notifikasjon --formatter kafka.tools.DefaultMessageFormatter --property print.key=true --property print.value=true`
  * _from the beginning_ (legg til `--from-beginning`)
    * `kafka-console-consumer --bootstrap-server localhost:9092 --topic fager.notifikasjon --formatter kafka.tools.DefaultMessageFormatter --property print.key=true --property print.value=true --from-beginning`
* produce til en topic
  * `kafka-console-producer --bootstrap-server localhost:9092 --topic fager.notifikasjon`
* delete messages from a topic
  * `kafka-delete-records.sh --bootstrap-server $KAFKA_BROKERS --offset-json-file delete-records.json`
* inspect a consumer group
  * `kafka-consumer-groups.sh --bootstrap-server $KAFKA_BROKERS --command-config /tmp/kafka.properties --group query-model-builder --describe` 
* adjust offset of a consumer group (requires group is inactive, i.e. no running consumers)
  * `kafka-consumer-groups.sh --bootstrap-server $KAFKA_BROKERS --command-config /tmp/kafka.properties --group query-model-builder --topic fager.notifikasjon --reset-offsets --to-earliest --execute`
  * `kafka-consumer-groups.sh --bootstrap-server $KAFKA_BROKERS --command-config /tmp/kafka.properties --group query-model-builder --topic fager.notifikasjon --reset-offsets --shift-by 1 --execute`
  * specify partition using `--topic topic:0,1,2`. e.g. reset offset for partition 14 to 5004:
  * `kafka-consumer-groups.sh --bootstrap-server $KAFKA_BROKERS --command-config /tmp/kafka.properties --group query-model-builder --topic fager.notifikasjon:14 --reset-offsets --to-offset 5004 --execute`

ref:
https://medium.com/@TimvanBaarsen/apache-kafka-cli-commands-cheat-sheet-a6f06eac01b
