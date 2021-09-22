#!/bin/bash

rm -rf .kafka-cli
mkdir -p .kafka-cli
pushd .kafka-cli
curl https://downloads.apache.org/kafka/2.7.1/kafka_2.13-2.7.1.tgz --output kafka.tgz
tar --strip-components=1 -xzf kafka.tgz
chmod +x bin/*
echo "
bootstrap.servers=localhost:9092
" > kafka.properties
popd