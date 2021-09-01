#!/bin/sh

minikube start

minikube mount .:/arbeidsgiver-notifikasjon-produsent-api &

echo "sleep 5 ..."
sleep 5

minikube kubectl -- --context=minikube apply -f ./k8s-local-postgres.yaml

echo "sleep 5 ..."
sleep 5

minikube kubectl -- --context=minikube port-forward pod/postgres 5432


