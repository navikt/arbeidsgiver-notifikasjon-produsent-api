package no.nav.arbeidsgiver.notifikasjon.produsent.api

import graphql.schema.idl.RuntimeWiring
import no.nav.arbeidsgiver.notifikasjon.Hendelse
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.coDataFetcher
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.wire
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.CoroutineKafkaProducer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.KafkaKey
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepository

class MutationNyStatusSak(
    private val kafkaProducer: CoroutineKafkaProducer<KafkaKey, Hendelse>,
    private val produsentRepository: ProdusentRepository,
) {

    fun wire(runtime: RuntimeWiring.Builder) {
        runtime.type("NyStatusSakResultat") {
            it.typeResolver {
                it.schema.getObjectType("NyStatusSakVellykket")
            }
        }

        runtime.wire("Mutation") {
            coDataFetcher("nyStatusSak") { env ->
                null
            }
            coDataFetcher("nyStatusSakByGrupperingsid") { env ->
                null
            }
        }
    }
}

