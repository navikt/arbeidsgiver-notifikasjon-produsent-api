#!/bin/sh

# stop on error
set -e

PROJECTS="fager-dev-24f2 fager-prod-dd77"
INSTANCES="bruker-api produsent-api ekstern-varsling kafka-reaper"
USERS="peter.brottveit.bock ken.gullaksen bendik.segrov.ibenholt"

# Kommandoen under er idempotent, så vidt jeg kan se. 
# Den som kjører kommandoen må ha "roles/cloudsql.admin" i prosjektet.


echo CREATING SQL USERS

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


#list users
#for project in $PROJECTS; do 
#    for instance in $INSTANCES; do
#        echo $project $instance
#        gcloud beta sql users list \
#            --instance=notifikasjon-$instance \
#            --project $project
#    done
#done


# Grant all accesses
ALWAYS_ACCESS_PROJECTS="fager-dev-24f2"
INSTANCES="bruker-api produsent-api ekstern-varsling kafka-reaper"
USERS="peter.brottveit.bock ken.gullaksen bendik.segrov.ibenholt"

echo GRANTING PERMANENT ACCESS
for project in $ALWAYS_ACCESS_PROJECTS; do
    for user in $USERS; do
        echo gcloud projects add-iam-policy-binding $project \
            --member=user:$user@nav.no \
            --role=roles/cloudsql.instanceUser
    done
done

