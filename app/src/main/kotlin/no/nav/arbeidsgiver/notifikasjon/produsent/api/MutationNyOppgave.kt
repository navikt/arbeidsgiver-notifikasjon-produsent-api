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
import no.nav.arbeidsgiver.notifikasjon.virksomhetsnummer
import java.util.*

class MutationNyOppgave(
    private val kafkaProducer: CoroutineKafkaProducer<KafkaKey, Hendelse>,
    private val produsentRepository: ProdusentRepository,
) {
    private val log = logger()

    fun wire(runtime: RuntimeWiring.Builder) {
        runtime.resolveSubtypes<NyOppgaveResultat>()

        runtime.wire("Mutation") {
            coDataFetcher("nyOppgave") { env ->
                nyOppgave(
                    env.getContext(),
                    env.getTypedArgument("nyOppgave"),
                )
            }
        }
    }

    data class NyOppgaveInput(
        val mottaker: MottakerInput,
        val notifikasjon: QueryMineNotifikasjoner.NotifikasjonData,
        val metadata: MetadataInput,
        val eksternVarsel: List<EksternVarselInput>,
    ) {
        fun tilDomene(id: UUID, produsentId: String, kildeAppNavn: String): Hendelse.OppgaveOpprettet {
            val mottaker = mottaker.tilDomene()
            return Hendelse.OppgaveOpprettet(
                hendelseId = id,
                notifikasjonId = id,
                merkelapp = notifikasjon.merkelapp,
                tekst = notifikasjon.tekst,
                grupperingsid = metadata.grupperingsid,
                lenke = notifikasjon.lenke,
                eksternId = metadata.eksternId,
                mottaker = mottaker,
                opprettetTidspunkt = metadata.opprettetTidspunkt,
                virksomhetsnummer = mottaker.virksomhetsnummer,
                produsentId = produsentId,
                kildeAppNavn = kildeAppNavn,
            )
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
    sealed interface NyOppgaveResultat

    @JsonTypeName("NyOppgaveVellykket")
    data class NyOppgaveVellykket(
        val id: UUID,
        val eksternVarsel: List<NyEksternVarselResultat>,
    ) : NyOppgaveResultat

    private suspend fun nyOppgave(
        context: ProdusentAPI.Context,
        nyOppgave: NyOppgaveInput,
    ): NyOppgaveResultat {
        val produsent = hentProdusent(context) { error -> return error }

        tilgangsstyrNyNotifikasjon(
            produsent,
            nyOppgave.mottaker.tilDomene(),
            nyOppgave.notifikasjon.merkelapp,
        ) { error -> return error }

        val id = UUID.randomUUID()
        val domeneNyOppgave = nyOppgave.tilDomene(
            id = id,
            produsentId = produsent.id,
            kildeAppNavn = context.appName,
        )
        val eksisterende = produsentRepository.hentNotifikasjon(
            eksternId = domeneNyOppgave.eksternId,
            merkelapp = domeneNyOppgave.merkelapp,
        )

        return when {
            eksisterende == null -> {
                log.info("oppretter ny oppgave med id $id")
                kafkaProducer.sendHendelseMedKey(id, domeneNyOppgave)
                produsentRepository.oppdaterModellEtterHendelse(domeneNyOppgave)
                NyOppgaveVellykket(
                    id = id,
                    eksternVarsel = listOf()
                )
            }
            eksisterende.erDuplikatAv(domeneNyOppgave.tilProdusentModel()) -> {
                log.info("duplisert opprettelse av oppgave med id ${eksisterende.id}")
                NyOppgaveVellykket(
                    id = eksisterende.id,
                    eksternVarsel = listOf()
                )
            }
            else -> {
                Error.DuplikatEksternIdOgMerkelapp(
                    "notifikasjon med angitt eksternId og merkelapp finnes fra før"
                )
            }
        }
    }
}
