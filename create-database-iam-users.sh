#!/bin/sh

PROJECTS="fager-dev-24f2 fager-prod-dd77"
INSTANCES="bruker-api produsent-api ekstern-varsling kafka-reaper statistikk"
USERS="peter.brottveit.bock ken.gullaksen bendik.segrov.ibenholt"

# Kommandoen under er idempotent, så vidt jeg kan se. 
# Den som kjører kommandoen må ha "roles/cloudsql.admin" i prosjektet.

for project in $PROJECTS; do 
    for user in $USERS; do
        for instance in $INSTANCES; do

            if [ $project = fager-prod-dd77 -a $instance = ekstern-varsling ]; then
                continue
            fi
            
            echo gcloud beta sql users create $user@nav.no \
                --instance=notifikasjon-$instance \
                --project $project \
                --type=cloud_iam_user
        done
    done
done

#for project in $PROJECTS; do 
#    for instance in $INSTANCES; do
#        echo $project $instance
#        gcloud beta sql users list \
#            --instance=notifikasjon-$instance \
#            --project $project
#    done
#done
