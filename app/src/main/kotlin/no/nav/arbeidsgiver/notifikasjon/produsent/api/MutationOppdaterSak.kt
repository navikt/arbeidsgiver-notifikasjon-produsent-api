package no.nav.arbeidsgiver.notifikasjon.produsent.api

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import graphql.schema.idl.RuntimeWiring
import no.nav.arbeidsgiver.notifikasjon.Hendelse
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.coDataFetcher
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.getTypedArgument
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.resolveSubtypes
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.wire
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.CoroutineKafkaProducer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.KafkaKey
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.sendHendelseMedKey
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepository
import no.nav.arbeidsgiver.notifikasjon.produsent.tilProdusentModel
import java.util.*

class MutationOppdaterSak(
    private val kafkaProducer: CoroutineKafkaProducer<KafkaKey, Hendelse>,
    private val produsentRepository: ProdusentRepository,
) {

    fun wire(runtime: RuntimeWiring.Builder) {
        runtime.type("OppdaterSakResultat") {
            it.typeResolver {
                it.schema.getObjectType("OppdaterSakVellykket")
            }
        }

        runtime.wire("Mutation") {
            coDataFetcher("oppdaterSak") { env ->
                null
            }
        }
    }
}

