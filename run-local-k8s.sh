#!/bin/sh

minikube start

minikube mount .:/arbeidsgiver-notifikasjon-produsent-api &

echo "sleep 5 ..."
sleep 5

minikube kubectl -- --context=minikube apply -f ./k8s-local.yaml

echo "sleep 5 ..."
sleep 5

minikube kubectl -- --context=minikube port-forward pod/postgres 5432 & \
  minikube kubectl -- --context=minikube port-forward pod/broker 9092 9101 & \
  minikube kubectl -- --context=minikube port-forward pod/zookeeper 2181


