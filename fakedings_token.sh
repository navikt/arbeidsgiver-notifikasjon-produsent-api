#!/bin/sh

fakedings_token=$(curl -s -X POST --location "https://fakedings.intern.dev.nav.no/fake/custom" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "sub=someproducer&aud=produsent-api")

echo $fakedings_token
