package no.nav.arbeidsgiver.notifikasjon.produsent.api

import graphql.schema.DataFetchingEnvironment
import kotlinx.coroutines.CoroutineScope
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepository


object ProdusentAPI {
    data class Context(
        val appName: String,
        val produsent: Produsent?,
        override val coroutineScope: CoroutineScope
    ) : WithCoroutineScope

    fun newGraphQL(
        kafkaProducer: HendelseProdusent,
        produsentRepository: ProdusentRepository,
    ): TypedGraphQL<Context> {

        val hendelseDispatcher = HendelseDispatcher(kafkaProducer, produsentRepository)

        fun queryWhoami(env: DataFetchingEnvironment): String {
            // TODO: returner hele context objectet som struct
            return env.notifikasjonContext<Context>().appName
        }

        return TypedGraphQL(
            createGraphQL("/produsent.graphql") {
                directive("Validate", ValidateDirective)

                scalar(Scalars.ISO8601DateTime)
                scalar(Scalars.ISO8601LocalDateTime)
                scalar(Scalars.ISO8601Duration)
                scalar(Scalars.ISO8601Date)

                resolveSubtypes<Error>()

                wire("Query") {
                    dataFetcher("whoami", ::queryWhoami)
                }

                QueryNotifikasjoner(produsentRepository).wire(this)
                QuerySak(produsentRepository).wire(this)
                MutationHardDeleteSak(hendelseDispatcher, produsentRepository).wire(this)
                MutationHardDeleteNotifikasjon(hendelseDispatcher, produsentRepository).wire(this)
                MutationNyBeskjed(hendelseDispatcher, produsentRepository).wire(this)
                MutationNyOppgave(hendelseDispatcher, produsentRepository).wire(this)
                MutationOppgaveUtfoert(hendelseDispatcher, produsentRepository).wire(this)
                MutationOppgaveUtgaatt(hendelseDispatcher, produsentRepository).wire(this)
                MutationOppgaveUtsettFrist(hendelseDispatcher, produsentRepository).wire(this)
                MutationSoftDeleteSak(hendelseDispatcher, produsentRepository).wire(this)
                MutationSoftDeleteNotifikasjon(hendelseDispatcher, produsentRepository).wire(this)
                MutationNySak(hendelseDispatcher, produsentRepository).wire(this)
                MutationNyStatusSak(hendelseDispatcher, produsentRepository).wire(this)
                MutationKalenderavtale(hendelseDispatcher, produsentRepository).wire(this)
                MutationNesteStegSak(hendelseDispatcher, produsentRepository).wire(this)
                MutationTilleggsinformasjonSak(hendelseDispatcher, produsentRepository).wire(this)
                MutationOppgavePaaminnelse(hendelseDispatcher, produsentRepository).wire(this)
            }
        )
    }
}