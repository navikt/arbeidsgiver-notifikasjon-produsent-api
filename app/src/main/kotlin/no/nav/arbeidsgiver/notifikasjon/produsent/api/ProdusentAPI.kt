package no.nav.arbeidsgiver.notifikasjon.produsent.api

import graphql.schema.DataFetchingEnvironment
import kotlinx.coroutines.CoroutineScope
import no.nav.arbeidsgiver.notifikasjon.Hendelse
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.CoroutineKafkaProducer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.KafkaKey
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.createKafkaProducer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepository

object ProdusentAPI {
    data class Context(
        val appName: String,
        val produsent: Produsent?,
        override val coroutineScope: CoroutineScope
    ) : WithCoroutineScope

    fun newGraphQL(
        kafkaProducer: CoroutineKafkaProducer<KafkaKey, Hendelse> = createKafkaProducer(),
        produsentRepository: ProdusentRepository,
    ): TypedGraphQL<Context> {

        fun queryWhoami(env: DataFetchingEnvironment): String {
            // TODO: returner hele context objectet som struct
            return env.getContext<Context>().appName
        }

        return TypedGraphQL(
            createGraphQL("/produsent.graphql") {
                directive("Validate", ValidateDirective)

                scalar(Scalars.ISO8601DateTime)
                scalar(Scalars.ISO8601LocalDateTime)

                resolveSubtypes<Error>()

                wire("Query") {
                    dataFetcher("whoami", ::queryWhoami)
                }

                QueryMineNotifikasjoner(produsentRepository).wire(this)
                MutationHardDelete(kafkaProducer, produsentRepository).wire(this)
                MutationNyBeskjed(kafkaProducer, produsentRepository).wire(this)
                MutationNyOppgave(kafkaProducer, produsentRepository).wire(this)
                MutationOppgaveUtfoert(kafkaProducer, produsentRepository).wire(this)
                MutationSoftDelete(kafkaProducer, produsentRepository).wire(this)
                MutationNySak(kafkaProducer, produsentRepository).wire(this)
                MutationOppdaterSak(kafkaProducer, produsentRepository).wire(this)
            }
        )
    }
}
