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

class MutationNyBeskjed(
    private val kafkaProducer: CoroutineKafkaProducer<KafkaKey, Hendelse>,
    private val produsentRepository: ProdusentRepository,
) {
    private val log = logger()

    fun wire(runtime: RuntimeWiring.Builder) {
        runtime.resolveSubtypes<NyBeskjedResultat>()

        runtime.wire("Mutation") {
            coDataFetcher("nyBeskjed") { env ->
                nyBeskjed(
                    env.getContext(),
                    env.getTypedArgument("nyBeskjed"),
                )
            }
        }
    }


    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
    sealed interface NyBeskjedResultat

    @JsonTypeName("NyBeskjedVellykket")
    data class NyBeskjedVellykket(
        val id: UUID,
        val eksterneVarsler: List<NyEksternVarselResultat>
    ) : NyBeskjedResultat

    data class NyBeskjedInput(
        val mottaker: MottakerInput,
        val notifikasjon: QueryMineNotifikasjoner.NotifikasjonData,
        val metadata: MetadataInput,
        val eksterneVarsler: List<EksterntVarselInput>
    ) {
        fun tilDomene(
            id: UUID,
            produsentId: String,
            kildeAppNavn: String,
        ): Hendelse.BeskjedOpprettet {
            val virksomhetsnummer = listOfNotNull(
                metadata.virksomhetsnummer,
                mottaker.altinn?.virksomhetsnummer,
                mottaker.naermesteLeder?.virksomhetsnummer
            )
                .toSet()
                .single()
            val mottaker = mottaker.tilDomene(virksomhetsnummer)
            return Hendelse.BeskjedOpprettet(
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

    private suspend fun nyBeskjed(
        context: ProdusentAPI.Context,
        nyBeskjed: NyBeskjedInput,
    ): NyBeskjedResultat {
        val produsent = hentProdusent(context) { error -> return error }
        val id = UUID.randomUUID()
        val domeneNyBeskjed = nyBeskjed.tilDomene(
            id = id,
            produsentId = produsent.id,
            kildeAppNavn = context.appName,
        )

        tilgangsstyrNyNotifikasjon(
            produsent,
            domeneNyBeskjed.mottakere,
            nyBeskjed.notifikasjon.merkelapp
        ) { error ->
            return error
        }

        val eksisterende = produsentRepository.hentNotifikasjon(
            eksternId = domeneNyBeskjed.eksternId,
            merkelapp = domeneNyBeskjed.merkelapp
        )

        return when {
            eksisterende == null -> {
                log.info("oppretter ny beskjed med id $id")
                kafkaProducer.sendHendelseMedKey(id, domeneNyBeskjed)
                produsentRepository.oppdaterModellEtterHendelse(domeneNyBeskjed)
                NyBeskjedVellykket(
                    id = id,
                    eksterneVarsler = domeneNyBeskjed.eksterneVarsler.map {
                        NyEksternVarselResultat(it.varselId)
                    }
                )
            }
            eksisterende.erDuplikatAv(domeneNyBeskjed.tilProdusentModel()) -> {
                log.info("duplisert opprettelse av beskjed med id ${eksisterende.id}")
                NyBeskjedVellykket(
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


