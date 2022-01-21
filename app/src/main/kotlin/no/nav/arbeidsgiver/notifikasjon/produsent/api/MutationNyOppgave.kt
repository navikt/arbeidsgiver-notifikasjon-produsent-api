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
        val eksterneVarsler: List<EksterntVarselInput>,
    ) {
        fun tilDomene(
            id: UUID,
            produsentId: String,
            kildeAppNavn: String,
            virksomhetsnummer: String,
        ): Hendelse.OppgaveOpprettet {
            val mottaker = mottaker.tilDomene(virksomhetsnummer)
            return Hendelse.OppgaveOpprettet(
                hendelseId = id,
                notifikasjonId = id,
                merkelapp = notifikasjon.merkelapp,
                tekst = notifikasjon.tekst,
                grupperingsid = metadata.grupperingsid,
                lenke = notifikasjon.lenke,
                eksternId = metadata.eksternId,
                mottakere = listOf(mottaker),
                opprettetTidspunkt = metadata.opprettetTidspunkt,
                virksomhetsnummer = virksomhetsnummer,
                produsentId = produsentId,
                kildeAppNavn = kildeAppNavn,
                eksterneVarsler = eksterneVarsler.map(EksterntVarselInput::tilDomene)
            )
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
    sealed interface NyOppgaveResultat

    @JsonTypeName("NyOppgaveVellykket")
    data class NyOppgaveVellykket(
        val id: UUID,
        val eksterneVarsler: List<NyEksternVarselResultat>,
    ) : NyOppgaveResultat

    private suspend fun nyOppgave(
        context: ProdusentAPI.Context,
        nyOppgave: NyOppgaveInput,
    ): NyOppgaveResultat {
        val produsent = hentProdusent(context) { error -> return error }

        val virksomhetsnummer = listOfNotNull(
            nyOppgave.metadata.virksomhetsnummer,
            nyOppgave.mottaker.altinn?.virksomhetsnummer,
            nyOppgave.mottaker.naermesteLeder?.virksomhetsnummer
        )
            .toSet()
            .single()


        tilgangsstyrNyNotifikasjon(
            produsent,
            nyOppgave.mottaker.tilDomene(virksomhetsnummer),
            nyOppgave.notifikasjon.merkelapp,
        ) { error -> return error }

        val id = UUID.randomUUID()
        val domeneNyOppgave = nyOppgave.tilDomene(
            id = id,
            produsentId = produsent.id,
            kildeAppNavn = context.appName,
            virksomhetsnummer = virksomhetsnummer,
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
                    eksterneVarsler = domeneNyOppgave.eksterneVarsler.map {
                        NyEksternVarselResultat(it.varselId)
                    }
                )
            }
            eksisterende.erDuplikatAv(domeneNyOppgave.tilProdusentModel()) -> {
                log.info("duplisert opprettelse av oppgave med id ${eksisterende.id}")
                NyOppgaveVellykket(
                    id = eksisterende.id,
                    eksterneVarsler = eksisterende.eksterneVarsler.map {
                        NyEksternVarselResultat(it.varselId)
                    }
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
