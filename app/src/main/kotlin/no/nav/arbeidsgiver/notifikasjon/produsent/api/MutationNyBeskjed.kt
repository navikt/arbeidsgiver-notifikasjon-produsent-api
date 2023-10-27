package no.nav.arbeidsgiver.notifikasjon.produsent.api

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import graphql.schema.idl.RuntimeWiring
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.BeskjedOpprettet
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepository
import no.nav.arbeidsgiver.notifikasjon.produsent.tilProdusentModel
import java.util.*

internal class MutationNyBeskjed(
    private val hendelseDispatcher: HendelseDispatcher,
    private val produsentRepository: ProdusentRepository,
) {
    private val log = logger()

    fun wire(runtime: RuntimeWiring.Builder) {
        runtime.resolveSubtypes<NyBeskjedResultat>()
        runtime.wire("Mutation") {
            coDataFetcher("nyBeskjed") { env ->
                nyBeskjed(
                    env.notifikasjonContext(),
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
        val eksterneVarsler: List<NyEksterntVarselResultat>
    ) : NyBeskjedResultat

    data class NyBeskjedInput(
        val mottakere: List<MottakerInput>,
        val mottaker: MottakerInput?,
        val notifikasjon: QueryMineNotifikasjoner.NotifikasjonData,
        val metadata: MetadataInput,
        val eksterneVarsler: List<EksterntVarselInput>
    ) {
        fun tilDomene(
            id: UUID,
            produsentId: String,
            kildeAppNavn: String,
            sakId: UUID?,
        ): BeskjedOpprettet {
            val alleMottakere = listOfNotNull(mottaker) + mottakere
            return BeskjedOpprettet(
                hendelseId = id,
                notifikasjonId = id,
                merkelapp = notifikasjon.merkelapp,
                tekst = notifikasjon.tekst,
                grupperingsid = metadata.grupperingsid,
                lenke = notifikasjon.lenke,
                eksternId = metadata.eksternId,
                mottakere = alleMottakere.map { it.tilDomene(metadata.virksomhetsnummer) },
                opprettetTidspunkt = metadata.opprettetTidspunkt,
                virksomhetsnummer = metadata.virksomhetsnummer,
                produsentId = produsentId,
                kildeAppNavn = kildeAppNavn,
                eksterneVarsler = eksterneVarsler.map {
                    it.tilDomene(metadata.virksomhetsnummer)
                },
                hardDelete = metadata.hardDelete?.tilDomene(),
                sakId = sakId,
            )
        }
    }

    private suspend fun nyBeskjed(
        context: ProdusentAPI.Context,
        nyBeskjed: NyBeskjedInput,
    ): NyBeskjedResultat {
        val produsent = hentProdusent(context) { error -> return error }
        val sakId : UUID? = nyBeskjed.metadata.grupperingsid?.let { grupperingsid ->
            try {
                hentSak(produsentRepository, grupperingsid, nyBeskjed.notifikasjon.merkelapp) {
                    TODO("make sak required by returning this error")
                }.id
            } catch (e: Exception) {
                // noop, sak not required
                null
            }
        }
        val id = UUID.randomUUID()
        val domeneNyBeskjed = try {
            nyBeskjed.tilDomene(
                id = id,
                produsentId = produsent.id,
                kildeAppNavn = context.appName,
                sakId = sakId,
            )
        } catch (e: UkjentRolleException) {
            return Error.UkjentRolle(e.message!!)
        }

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
                hendelseDispatcher.send(domeneNyBeskjed)
                NyBeskjedVellykket(
                    id = id,
                    eksterneVarsler = domeneNyBeskjed.eksterneVarsler.map {
                        NyEksterntVarselResultat(it.varselId)
                    }
                )
            }
            eksisterende.erDuplikatAv(domeneNyBeskjed.tilProdusentModel()) -> {
                log.info("duplisert opprettelse av beskjed med id ${eksisterende.id}")
                NyBeskjedVellykket(
                    id = eksisterende.id,
                    eksterneVarsler = eksisterende.eksterneVarsler.map {
                        NyEksterntVarselResultat(it.varselId)
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


