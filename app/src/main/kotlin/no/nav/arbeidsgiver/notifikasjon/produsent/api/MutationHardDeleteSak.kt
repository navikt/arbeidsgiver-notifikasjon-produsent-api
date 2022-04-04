package no.nav.arbeidsgiver.notifikasjon.produsent.api

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import graphql.schema.idl.RuntimeWiring
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.HardDelete
import no.nav.arbeidsgiver.notifikasjon.HendelseModel.Hendelse
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.coDataFetcher
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.getTypedArgument
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.resolveSubtypes
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.wire
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.CoroutineKafkaProducer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.KafkaKey
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.sendHendelse
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentModel
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepository
import java.time.OffsetDateTime
import java.util.*

class MutationHardDeleteSak(
    private val kafkaProducer: CoroutineKafkaProducer<KafkaKey, Hendelse>,
    private val produsentRepository: ProdusentRepository,
) {
    fun wire(runtime: RuntimeWiring.Builder) {
        runtime.resolveSubtypes<HardDeleteSakResultat>()

        runtime.wire("Mutation") {
            coDataFetcher("hardDeleteSak") { env ->
                hardDelete(
                    context = env.getContext(),
                    id = env.getTypedArgument("id")
                )
            }
            coDataFetcher("hardDeleteSakByGrupperingsid") { env ->
                hardDelete(
                    context = env.getContext(),
                    grupperingsid = env.getTypedArgument("grupperingsid"),
                    merkelapp = env.getTypedArgument("merkelapp"),
                )
            }
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
    sealed interface HardDeleteSakResultat

    @JsonTypeName("HardDeleteSakVellykket")
    data class HardDeleteSakVellykket(
        val id: UUID
    ) : HardDeleteSakResultat

    private suspend fun hardDelete(
        context: ProdusentAPI.Context,
        id: UUID,
    ): HardDeleteSakResultat {
        val sak = hentSak(produsentRepository, id) { error -> return error }
        return hardDelete(context, sak)
    }

    private suspend fun hardDelete(
        context: ProdusentAPI.Context,
        grupperingsid: String,
        merkelapp: String,
    ): HardDeleteSakResultat {
        val sak = hentSak(produsentRepository, grupperingsid, merkelapp) { error -> return error }
        return hardDelete(context, sak)
    }

    private suspend fun hardDelete(
        context: ProdusentAPI.Context,
        sak: ProdusentModel.Sak,
    ): HardDeleteSakResultat {
        val produsent = hentProdusent(context) { error -> return error }
        tilgangsstyrMerkelapp(produsent, sak.merkelapp) { error -> return error }

        val hardDelete = HardDelete(
            hendelseId = UUID.randomUUID(),
            aggregateId = sak.id,
            virksomhetsnummer = sak.virksomhetsnummer,
            deletedAt = OffsetDateTime.now(),
            produsentId = produsent.id,
            kildeAppNavn = context.appName
        )

        kafkaProducer.sendHendelse(hardDelete)
        produsentRepository.oppdaterModellEtterHendelse(hardDelete)
        return HardDeleteSakVellykket(sak.id)
    }
}