package no.nav.arbeidsgiver.notifikasjon.produsent.api

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import graphql.schema.idl.RuntimeWiring
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveOpprettet
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentModel
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepository
import no.nav.arbeidsgiver.notifikasjon.produsent.tilProdusentModel
import java.time.LocalDate
import java.util.*

internal class MutationNyOppgave(
    private val hendelseDispatcher: HendelseDispatcher,
    private val produsentRepository: ProdusentRepository,
) {
    private val log = logger()

    fun wire(runtime: RuntimeWiring.Builder) {
        runtime.resolveSubtypes<NyOppgaveResultat>()

        runtime.wire("Mutation") {
            coDataFetcher("nyOppgave") { env ->
                nyOppgave(
                    env.notifikasjonContext(),
                    env.getTypedArgument("nyOppgave"),
                )
            }
        }
    }

    data class NyOppgaveInput(
        val mottaker: MottakerInput?,
        val mottakere: List<MottakerInput>,
        val notifikasjon: QueryNotifikasjoner.NotifikasjonData,
        val frist: LocalDate?,
        val paaminnelse: PaaminnelseInput?,
        val metadata: MetadataInput,
        val eksterneVarsler: List<EksterntVarselInput>,
    ) {
        fun tilDomene(
            id: UUID,
            produsentId: String,
            kildeAppNavn: String,
            sakId: UUID?,
        ): OppgaveOpprettet {
            val alleMottakere = listOfNotNull(mottaker) + mottakere
            return OppgaveOpprettet(
                hendelseId = id,
                notifikasjonId = id,
                merkelapp = notifikasjon.merkelapp,
                tekst = notifikasjon.tekst,
                grupperingsid = metadata.grupperingsid,
                lenke = notifikasjon.lenke,
                eksternId = metadata.eksternId,
                mottakere = alleMottakere.map { it.tilHendelseModel(metadata.virksomhetsnummer) },
                opprettetTidspunkt = metadata.opprettetTidspunkt,
                virksomhetsnummer = metadata.virksomhetsnummer,
                produsentId = produsentId,
                kildeAppNavn = kildeAppNavn,
                eksterneVarsler = eksterneVarsler.map {
                    it.tilHendelseModel(metadata.virksomhetsnummer)
                },
                påminnelse = paaminnelse?.tilDomene(
                    notifikasjonOpprettetTidspunkt = metadata.opprettetTidspunkt,
                    frist = frist,
                    startTidspunkt = null,
                    virksomhetsnummer = metadata.virksomhetsnummer,
                ),
                hardDelete = metadata.hardDelete?.tilHendelseModel(),
                frist = frist,
                sakId = sakId,
            )
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
    sealed interface NyOppgaveResultat

    @JsonTypeName("NyOppgaveVellykket")
    data class NyOppgaveVellykket(
        val id: UUID,
        val eksterneVarsler: List<NyEksterntVarselResultat>,
        val paaminnelse: PåminnelseResultat?,
    ) : NyOppgaveResultat

    @JsonTypeName("PaaminnelseResultat")
    data class PåminnelseResultat(
        val eksterneVarsler: List<NyEksterntVarselResultat>,
    )

    private suspend fun nyOppgave(
        context: ProdusentAPI.Context,
        nyOppgave: NyOppgaveInput,
    ): NyOppgaveResultat {
        val produsent = hentProdusent(context) { error -> return error }
        val sakId : UUID? = nyOppgave.metadata.grupperingsid?.let { grupperingsid ->
            runCatching {
                hentSak(produsentRepository, grupperingsid, nyOppgave.notifikasjon.merkelapp) {
                    TODO("make sak required by returning this error")
                }.id
            }.getOrNull()
        }
        val id = UUID.randomUUID()
        val domeneNyOppgave = try {
            nyOppgave.tilDomene(
                id = id,
                produsentId = produsent.id,
                kildeAppNavn = context.appName,
                sakId = sakId,
            )
        } catch (e: UkjentRolleException){
            return Error.UkjentRolle(e.message!!)
        } catch (e: UgyldigPåminnelseTidspunktException) {
            return Error.UgyldigPåminnelseTidspunkt(e.message!!)
        }

        tilgangsstyrNyNotifikasjon(
            produsent,
            domeneNyOppgave.mottakere,
            nyOppgave.notifikasjon.merkelapp,
        ) { error -> return error }

        val eksisterende = produsentRepository.hentNotifikasjon(
            eksternId = domeneNyOppgave.eksternId,
            merkelapp = domeneNyOppgave.merkelapp,
        )

        return when {
            eksisterende == null -> {
                log.info("oppretter ny oppgave med id $id")
                hendelseDispatcher.send(domeneNyOppgave)
                NyOppgaveVellykket(
                    id = id,
                    eksterneVarsler = domeneNyOppgave.eksterneVarsler.map {
                        NyEksterntVarselResultat(it.varselId)
                    },
                    paaminnelse = domeneNyOppgave.påminnelse?.let { påminnelse ->
                        PåminnelseResultat(
                            påminnelse.eksterneVarsler.map { varsel ->
                                NyEksterntVarselResultat(varsel.varselId)
                            }
                        )
                    }
                )
            }
            eksisterende.erDuplikatAv(domeneNyOppgave.tilProdusentModel()) &&
            eksisterende is ProdusentModel.Oppgave -> {
                log.info("duplisert opprettelse av oppgave med id ${eksisterende.id}")
                NyOppgaveVellykket(
                    id = eksisterende.id,
                    eksterneVarsler = eksisterende.eksterneVarsler.map {
                        NyEksterntVarselResultat(it.varselId)
                    },
                    paaminnelse = if (nyOppgave.paaminnelse == null)
                        null
                    else PåminnelseResultat(
                        eksisterende.påminnelseEksterneVarsler.map {
                            NyEksterntVarselResultat(it.varselId)
                        }
                    )
                )
            }
            else -> {
                log.warn(
                    "notifikasjon med angitt eksternId={} og merkelapp={} finnes fra før",
                    domeneNyOppgave.eksternId,
                    domeneNyOppgave.merkelapp
                )
                Error.DuplikatEksternIdOgMerkelapp(
                    "notifikasjon med angitt eksternId og merkelapp finnes fra før",
                    eksisterende.id
                )
            }
        }
    }
}

internal class UgyldigPåminnelseTidspunktException(message: String) : RuntimeException(message)