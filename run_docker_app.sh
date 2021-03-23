#!/bin/bash

docker network create my-net || true
docker build -t produsent-api app
dpid=$(docker run --rm -d --env KAFKA_BROKERS="broker:9092" --network my-net --name produsent-api -p 8080:8080 produsent-api)

sleep 5s
docker logs $dpid
echo "=> docker container started $pid"
echo "   stop using:"
echo docker stop $dpid
echo "   tail logs using:"
echo docker logs -f $dpid