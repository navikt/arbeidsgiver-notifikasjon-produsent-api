#!/bin/bash

docker build -t produsent-api app
dpid=$(docker run --rm -d --network host --name produsent-api -p 8080 produsent-api)

sleep 5s
docker logs $dpid
echo "=> docker container started $pid"
echo "   stop using:"
echo docker stop $dpid
echo "   tail logs using:"
echo docker logs -f $dpid