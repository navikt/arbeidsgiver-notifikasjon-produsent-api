# Om denne dokumentasjonen

Dokumentasjonen her publiseres til github pages og er tilgjengelig på https://navikt.github.io/arbeidsgiver-notifikasjon-produsent-api/
Dokumentasjonen er skrevet i html med unntak av API dokumentasjonen som er generert vha spectacl.

# Hvordan oppdatere dokumentasjonen

## Oppdatering av API dokumentasjon

Dette skjer automatisk ved push av endringer i skjema til main branch (Se github actions).

## Oppdatering av annen dokumentasjon

Denne dokumentasjonen er skrevet i html og kan oppdateres ved å endre på filene i `docs` mappen.
Kodeeksempler har syntax highlighting vha [rouge](https://github.com/rouge-ruby/rouge) 

For å lage kodeeksempler kan du enten bruke nettsiden https://rouge.fly.dev/ eller ta i bruk rouge fra cli:

installer ruby og rouge (med asdf):
```
brew install asdf
asdf plugin add ruby
asdf install ruby 3.3.0
asdf global ruby 3.3.0
gem update --system
gem install rouge
```

Lag kodeeksempler:
```
ruby highlight.rb eksempel.graphql
# eller:
ruby highlight.rb eksempel.graphql > eksempel.html
```