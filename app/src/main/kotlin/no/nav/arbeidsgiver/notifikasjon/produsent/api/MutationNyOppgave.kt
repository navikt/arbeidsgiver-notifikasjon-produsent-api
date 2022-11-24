package no.nav.arbeidsgiver.notifikasjon.produsent.api

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import graphql.schema.idl.RuntimeWiring
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.PåminnelseTidspunkt.Companion.createAndValidateEtterOpprettelse
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.PåminnelseTidspunkt.Companion.createAndValidateFørFrist
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.PåminnelseTidspunkt.Companion.createAndValidateKonkret
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.ISO8601Period
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn.AltinnRolle
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.coDataFetcher
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.getTypedArgument
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.resolveSubtypes
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.wire
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepository
import no.nav.arbeidsgiver.notifikasjon.produsent.tilProdusentModel
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
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
                    env.getContext(),
                    env.getTypedArgument("nyOppgave"),
                )
            }
        }
    }

    data class NyOppgaveInput(
        val mottaker: MottakerInput?,
        val mottakere: List<MottakerInput>,
        val notifikasjon: QueryMineNotifikasjoner.NotifikasjonData,
        val frist: LocalDate?,
        val paaminnelse: PaaminnelseInput?,
        val metadata: MetadataInput,
        val eksterneVarsler: List<EksterntVarselInput>,
    ) {
        suspend fun tilDomene(
            id: UUID,
            produsentId: String,
            kildeAppNavn: String,
            finnRolleId: suspend (String) -> AltinnRolle?,
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
                mottakere = alleMottakere.map { it.tilDomene(metadata.virksomhetsnummer, finnRolleId) },
                opprettetTidspunkt = metadata.opprettetTidspunkt,
                virksomhetsnummer = metadata.virksomhetsnummer,
                produsentId = produsentId,
                kildeAppNavn = kildeAppNavn,
                eksterneVarsler = eksterneVarsler.map {
                    it.tilDomene(metadata.virksomhetsnummer)
                },
                påminnelse = paaminnelse?.tilDomene(metadata.opprettetTidspunkt, frist),
                hardDelete = metadata.hardDelete?.tilDomene(),
                frist = frist,
            )
        }
    }

    data class PaaminnelseInput(
        val tidspunkt: PaaminnelseTidspunktInput,
        val eksterneVarsler: List<EksterntVarselInput>,
    ) {
        fun tilDomene(
            opprettetTidspunkt: OffsetDateTime,
            frist: LocalDate?
        ) : HendelseModel.Påminnelse = HendelseModel.Påminnelse(
            tidspunkt = tidspunkt.tilDomene(opprettetTidspunkt, frist),
            eksterneVarsler = emptyList()
        )
    }

    data class PaaminnelseTidspunktInput(
        val konkret: LocalDateTime?,
        val etterOpprettelse: ISO8601Period?,
        val foerFrist: ISO8601Period?,
    ) {
        fun tilDomene(
            opprettetTidspunkt: OffsetDateTime,
            frist: LocalDate?
        ): HendelseModel.PåminnelseTidspunkt = when {
            konkret != null -> createAndValidateKonkret(konkret, opprettetTidspunkt, frist)
            etterOpprettelse != null -> createAndValidateEtterOpprettelse(etterOpprettelse, opprettetTidspunkt, frist)
            foerFrist != null -> createAndValidateFørFrist(foerFrist, opprettetTidspunkt, frist)
            else -> throw RuntimeException("Feil format")
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
        val id = UUID.randomUUID()
        val domeneNyOppgave = try {
            nyOppgave.tilDomene(
                id = id,
                produsentId = produsent.id,
                kildeAppNavn = context.appName,
                finnRolleId = produsentRepository.altinnRolle::hentAltinnrolle
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
                log.warn(
                    "notifikasjon med angitt eksternId={} og merkelapp={} finnes fra før",
                    domeneNyOppgave.eksternId,
                    domeneNyOppgave.merkelapp
                )
                Error.DuplikatEksternIdOgMerkelapp(
                    "notifikasjon med angitt eksternId og merkelapp finnes fra før"
                )
            }
        }
    }
}

internal class UgyldigPåminnelseTidspunktException(message: String) : RuntimeException(message)