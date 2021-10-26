#!/bin/sh

usage() {
    echo "$1" >&2
    echo "usage:\n\t$0 dev|prod \$database [port]" >&2
    exit 1
}


case "$1" in
    dev)
        PROJECT=fager-dev-24f2
        ;;
    prod)
        PROJECT=faget-prod-dd77
        ;;
    *)
        usage "missing gcp project"
        ;;
esac
shift

case "$1" in
    "")
        usage "missing database instance name"
        ;;
    *)
        DB="$1"
        ;;
esac
shift

case "$1" in
    "") 
        PORT=5432
        ;;
    *) 
        PORT="$1"
        ;;
esac
shift
    
CONNECTION_NAME=$(gcloud sql instances describe "$DB" \
  --format="get(connectionName)" \
  --project $PROJECT);

cloud_sql_proxy -instances=${CONNECTION_NAME}=tcp:$PORT
