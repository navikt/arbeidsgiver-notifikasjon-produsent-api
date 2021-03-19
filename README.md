# produsent-api

## Ticks n' Trips

* start lokal kafka cluster
  * `docker-compose up -d`
* connect til broker
  * `docker exec -it broker bash`
* vis broker version
  * `kafka-broker-api-versions --bootstrap-server localhost:9092 --version`
  * `docker exec -it broker kafka-broker-api-versions --bootstrap-server localhost:9092 --version`
* create topic
  * `kafka-topics --zookeeper $ZK_HOSTS --create --topic $TOPIC_NAME --partitions 3 --replication-factor 3`
  * `docker exec -it broker kafka-topics --zookeeper $ZK_HOSTS --create --topic $TOPIC_NAME --partitions 3 --replication-factor 3`
    * legg til `--if-not-exists` for å kun opprette dersom den ikke eksisterer fra før
* list topics
  * `kafka-topics --zookeeper zookeeper:2181 --list` 
  * `kafka-topics --zookeeper zookeeper:2181 --list --exclude-internal` 
  * `docker exec -it broker kafka-topics --zookeeper zookeeper:2181 --list` 
  * `docker exec -it broker kafka-topics --zookeeper zookeeper:2181 --list --exclude-internal` 
* describe topic
  * `kafka-topics --zookeeper zookeeper:2181 --topic $TOPIC_NAME --describe`
  * `docker exec -it broker kafka-topics --zookeeper zookeeper:2181 --topic $TOPIC_NAME --describe`
* juster opp antall partisjoner for topic
  * `kafka-topics --zookeeper zookeeper:2181 --alter --topic $TOPIC_NAME --partitions 5`
  * `docker exec -it broker kafka-topics --zookeeper zookeeper:2181 --alter --topic $TOPIC_NAME --partitions 5`
* purge en topic (krever å sette retention lav, så vente, så slette retention)
  * `kafka-topics --zookeeper zookeeper:2181 --alter --entity-type topics --entity-name $TOPIC_NAME --add-config retention.ms=1000`
  * _a few moments later_
  * `kafka-topics --zookeeper zookeeper:2181 --alter --entity-type topics --entity-name $TOPIC_NAME --delete-config retention.ms`
  * `docker exec -it broker kafka-topics --zookeeper zookeeper:2181 --alter --entity-type topics --entity-name $TOPIC_NAME --add-config retention.ms=1000`
  * _a few docker exec moments later_
  * `docker exec -it broker kafka-topics --zookeeper zookeeper:2181 --alter --entity-type topics --entity-name $TOPIC_NAME --delete-config retention.ms`
* delete topic
  * `kafka-topics --zookeeper zookeeper:2181 --delete --topic $TOPIC_NAME`
  * `docker exec -it broker kafka-topics --zookeeper zookeeper:2181  --delete --topic $TOPIC_NAME`
* consume en topic og print til console (default from latest)
  * `kafka-console-consumer --bootstrap-server localhost:9092 --topic $TOPIC_NAME --formatter kafka.tools.DefaultMessageFormatter --property print.key=true --property print.value=true`
  * `docker exec -it broker kafka-console-consumer --bootstrap-server localhost:9092 --topic $TOPIC_NAME --formatter kafka.tools.DefaultMessageFormatter --property print.key=true --property print.value=true`
  * _from the beginning_ (legg til `--from-beginning`)
    * `kafka-console-consumer --bootstrap-server localhost:9092 --topic $TOPIC_NAME --formatter kafka.tools.DefaultMessageFormatter --property print.key=true --property print.value=true --from-beginning`
    * `docker exec -it broker kafka-console-consumer --bootstrap-server localhost:9092 --topic $TOPIC_NAME --formatter kafka.tools.DefaultMessageFormatter --property print.key=true --property print.value=true --from-beginning`
 
ref:
https://medium.com/@TimvanBaarsen/apache-kafka-cli-commands-cheat-sheet-a6f06eac01b