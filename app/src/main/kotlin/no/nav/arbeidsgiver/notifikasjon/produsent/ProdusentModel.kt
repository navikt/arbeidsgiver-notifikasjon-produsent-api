package no.nav.arbeidsgiver.notifikasjon.produsent

import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database

interface ProdusentModel

class ProdusentModelImpl(
    private val database: Database
): ProdusentModel