#!/usr/bin/env sh

echo "
security.protocol=SSL
ssl.keystore.location=$KAFKA_KEYSTORE_PATH
ssl.keystore.password=$KAFKA_CREDSTORE_PASSWORD
ssl.truststore.location=$KAFKA_TRUSTSTORE_PATH
ssl.truststore.password=$KAFKA_CREDSTORE_PASSWORD
bootstrap.servers=$KAFKA_BROKERS
" > /tmp/kafka.properties