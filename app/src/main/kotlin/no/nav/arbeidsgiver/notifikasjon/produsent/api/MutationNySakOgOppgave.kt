package no.nav.arbeidsgiver.notifikasjon.produsent.api

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import graphql.schema.idl.RuntimeWiring
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.NyStatusSak
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SakOpprettet
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logging.logger
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentModel
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepository
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepository.AggregateType
import no.nav.arbeidsgiver.notifikasjon.produsent.tilProdusentModel
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

internal class MutationNySakOgOppgave(
    private val hendelseDispatcher: HendelseDispatcher,
    private val produsentRepository: ProdusentRepository,
) {
    private val log = logger()

    fun wire(runtime: RuntimeWiring.Builder) {
        runtime.resolveSubtypes<NySakOgOppgaveResultat>()

        runtime.wire("Mutation") {
            coDataFetcher("nySakOgOppgave") { env ->
                val sak = env.getTypedArgument<NySakOgOppgaveSakInput>("sak")
                nySakOgOppgave(
                    context = env.notifikasjonContext(),
                    nySak = MutationNySak.NySakInput(
                        grupperingsid = env.getTypedArgument("grupperingsid"),
                        merkelapp = env.getTypedArgument("merkelapp"),
                        virksomhetsnummer = env.getTypedArgument("virksomhetsnummer"),
                        mottakere = env.getTypedArgument("mottakere"),
                        tittel = sak.tittel,
                        tilleggsinformasjon = sak.tilleggsinformasjon,
                        lenke = sak.lenke,
                        status = SaksStatusInput(
                            status = sak.initiellStatus,
                            tidspunkt = sak.tidspunkt,
                            overstyrStatustekstMed = sak.overstyrStatustekstMed,
                        ),
                        nesteSteg = sak.nesteSteg,
                        hardDelete = sak.hardDelete,
                    ),
                    nyOppgave = env.getTypedArgument("oppgave"),
                )
            }
        }
    }

    private suspend fun nySakOgOppgave(
        context: ProdusentAPI.Context,
        nySak: MutationNySak.NySakInput,
        nyOppgave: NySakOgOppgaveOppgaveInput,
    ): NySakOgOppgaveResultat {
        val produsent = hentProdusent(context) { error -> return error }
        val sakId = UUID.randomUUID()
        val oppgaveId = UUID.randomUUID()
        val hendelser = byggHendelser(nySak, nyOppgave, sakId, oppgaveId, produsent.id, context.appName) { error -> return error }

        tilgangsstyrNyNotifikasjon(produsent, hendelser.sakOpprettet.mottakere, hendelser.sakOpprettet.merkelapp) { error -> return error }

        val eksisterendeSak = produsentRepository.hentSak(
            grupperingsid = hendelser.sakOpprettet.grupperingsid,
            merkelapp = hendelser.sakOpprettet.merkelapp,
        )
        val erHardDeleted = produsentRepository.erHardDeleted(
            type = AggregateType.SAK,
            grupperingsid = hendelser.sakOpprettet.grupperingsid,
            merkelapp = hendelser.sakOpprettet.merkelapp,
        )

        return when {
            eksisterendeSak == null && erHardDeleted ->
                Error.DuplikatGrupperingsidEtterDelete("sak med angitt grupperings-id og merkelapp har vært brukt tidligere")
            eksisterendeSak != null && nySak.erDuplikatAv(eksisterendeSak) ->
                håndterDuplikatSak(eksisterendeSak, hendelser, nyOppgave, oppgaveId)
            eksisterendeSak != null ->
                Error.DuplikatGrupperingsid("sak med angitt grupperings-id og merkelapp finnes fra før", eksisterendeSak.id)
            else ->
                opprettNySakOgOppgave(hendelser, sakId, oppgaveId)
        }
    }

    private data class Hendelser(
        val sakOpprettet: SakOpprettet,
        val statusoppdatering: NyStatusSak,
        val oppgave: OppgaveOpprettet,
    )

    private inline fun byggHendelser(
        nySak: MutationNySak.NySakInput,
        nyOppgave: NySakOgOppgaveOppgaveInput,
        sakId: UUID,
        oppgaveId: UUID,
        produsentId: String,
        kildeAppNavn: String,
        onError: (NySakOgOppgaveResultat) -> Nothing,
    ): Hendelser {
        val mottattTidspunkt = OffsetDateTime.now()
        val sakOpprettet = try {
            nySak.somSakOpprettetHendelse(
                id = sakId,
                produsentId = produsentId,
                kildeAppNavn = kildeAppNavn,
                mottattTidspunkt = mottattTidspunkt,
            )
        } catch (e: UkjentRolleException) {
            onError(Error.UkjentRolle(e.message!!))
        }
        val statusoppdatering = nySak.somNyStatusSakHendelse(
            hendelseId = UUID.randomUUID(),
            sakId = sakId,
            produsentId = produsentId,
            kildeAppNavn = kildeAppNavn,
            mottattTidspunkt = mottattTidspunkt,
        )
        val oppgave = try {
            nyOppgave.tilDomene(
                id = oppgaveId,
                produsentId = produsentId,
                kildeAppNavn = kildeAppNavn,
                sakId = sakId,
                grupperingsid = nySak.grupperingsid,
                merkelapp = nySak.merkelapp,
                virksomhetsnummer = nySak.virksomhetsnummer,
                mottakere = nySak.mottakere,
            )
        } catch (e: UkjentRolleException) {
            onError(Error.UkjentRolle(e.message!!))
        } catch (e: UgyldigPåminnelseTidspunktException) {
            onError(Error.UgyldigPåminnelseTidspunkt(e.message!!))
        }
        return Hendelser(sakOpprettet, statusoppdatering, oppgave)
    }

    private suspend fun opprettNySakOgOppgave(
        hendelser: Hendelser,
        sakId: UUID,
        oppgaveId: UUID,
    ): NySakOgOppgaveResultat {
        val eksisterendeOppgave = produsentRepository.hentNotifikasjon(
            eksternId = hendelser.oppgave.eksternId,
            merkelapp = hendelser.oppgave.merkelapp,
        )
        if (eksisterendeOppgave != null) {
            log.warn("notifikasjon med angitt eksternId={} og merkelapp={} finnes fra før", hendelser.oppgave.eksternId, hendelser.oppgave.merkelapp)
            return Error.DuplikatEksternIdOgMerkelapp("notifikasjon med angitt eksternId og merkelapp finnes fra før", eksisterendeOppgave.id)
        }
        log.info("oppretter ny sak med id $sakId og oppgave med id $oppgaveId")
        hendelseDispatcher.send(hendelser.sakOpprettet, hendelser.statusoppdatering, hendelser.oppgave)
        check(produsentRepository.hentSak(sakId) != null) {
            "Sak med id $sakId ble produsert til kafka men ble ikke lagret i produsent-databasen. " +
            "Dette er sannsynligvis en race condition — to saker med samme koordinat opprettet samtidig."
        }
        check(produsentRepository.hentNotifikasjon(oppgaveId) != null) {
            "Oppgave med id $oppgaveId ble produsert til kafka men ble ikke lagret i produsent-databasen. " +
            "Dette er sannsynligvis en race condition — to notifikasjoner med samme koordinat opprettet samtidig."
        }
        return NySakOgOppgaveVellykket(
            sakId = sakId,
            oppgaveId = oppgaveId,
            eksterneVarsler = hendelser.oppgave.eksterneVarsler.map { NyEksterntVarselResultat(it.varselId) },
            paaminnelse = hendelser.oppgave.påminnelse?.let { påminnelse ->
                MutationNyOppgave.PåminnelseResultat(påminnelse.eksterneVarsler.map { NyEksterntVarselResultat(it.varselId) })
            },
        )
    }

    private suspend fun håndterDuplikatSak(
        eksisterendeSak: ProdusentModel.Sak,
        hendelser: Hendelser,
        nyOppgave: NySakOgOppgaveOppgaveInput,
        oppgaveId: UUID,
    ): NySakOgOppgaveResultat {
        hendelseDispatcher.sendStatusoppdateringForDuplikatSak(eksisterendeSak, hendelser.statusoppdatering)

        val eksisterendeOppgave = produsentRepository.hentNotifikasjon(
            eksternId = hendelser.oppgave.eksternId,
            merkelapp = hendelser.oppgave.merkelapp,
        )
        return when {
            eksisterendeOppgave != null &&
            eksisterendeOppgave.erDuplikatAv(hendelser.oppgave.tilProdusentModel()) &&
            eksisterendeOppgave is ProdusentModel.Oppgave -> {
                log.info("duplisert opprettelse av oppgave med id ${eksisterendeOppgave.id}")
                NySakOgOppgaveVellykket(
                    sakId = eksisterendeSak.id,
                    oppgaveId = eksisterendeOppgave.id,
                    eksterneVarsler = eksisterendeOppgave.eksterneVarsler.map { NyEksterntVarselResultat(it.varselId) },
                    paaminnelse = if (nyOppgave.paaminnelse == null) null
                                  else MutationNyOppgave.PåminnelseResultat(
                                      eksisterendeOppgave.påminnelseEksterneVarsler.map { NyEksterntVarselResultat(it.varselId) }
                                  ),
                )
            }
            eksisterendeOppgave != null -> {
                log.warn("notifikasjon med angitt eksternId={} og merkelapp={} finnes fra før", hendelser.oppgave.eksternId, hendelser.oppgave.merkelapp)
                Error.DuplikatEksternIdOgMerkelapp("notifikasjon med angitt eksternId og merkelapp finnes fra før", eksisterendeOppgave.id)
            }
            else -> {
                log.info("oppretter ny oppgave med id $oppgaveId mot eksisterende sak med id ${eksisterendeSak.id}")
                hendelseDispatcher.send(hendelser.oppgave.copy(sakId = eksisterendeSak.id))
                NySakOgOppgaveVellykket(
                    sakId = eksisterendeSak.id,
                    oppgaveId = oppgaveId,
                    eksterneVarsler = hendelser.oppgave.eksterneVarsler.map { NyEksterntVarselResultat(it.varselId) },
                    paaminnelse = hendelser.oppgave.påminnelse?.let { påminnelse ->
                        MutationNyOppgave.PåminnelseResultat(påminnelse.eksterneVarsler.map { NyEksterntVarselResultat(it.varselId) })
                    },
                )
            }
        }
    }

    data class NySakOgOppgaveSakInput(
        val tittel: String,
        val tilleggsinformasjon: String?,
        val lenke: String?,
        val initiellStatus: SaksStatus,
        val nesteSteg: String?,
        val tidspunkt: OffsetDateTime?,
        val overstyrStatustekstMed: String?,
        val hardDelete: FutureTemporalInput?,
    )

    data class NySakOgOppgaveOppgaveInput(
        val eksternId: String,
        val tekst: String,
        val lenke: String,
        val frist: LocalDate?,
        val opprettetTidspunkt: OffsetDateTime?,
        val hardDelete: FutureTemporalInput?,
        val eksterneVarsler: List<EksterntVarselInput>,
        val paaminnelse: PaaminnelseInput?,
    ) {
        init {
            Validators.compose(
                Validators.MaxLength("oppgave.tekst", 300),
                Validators.NonIdentifying("oppgave.tekst")
            )(tekst)
        }
        fun tilDomene(
            id: UUID,
            produsentId: String,
            kildeAppNavn: String,
            sakId: UUID,
            grupperingsid: String,
            merkelapp: String,
            virksomhetsnummer: String,
            mottakere: List<MottakerInput>,
        ): OppgaveOpprettet = OppgaveOpprettet(
            hendelseId = id,
            notifikasjonId = id,
            merkelapp = merkelapp,
            tekst = tekst,
            grupperingsid = grupperingsid,
            lenke = lenke,
            eksternId = eksternId,
            mottakere = mottakere.map { it.tilHendelseModel(virksomhetsnummer) },
            opprettetTidspunkt = opprettetTidspunkt,
            virksomhetsnummer = virksomhetsnummer,
            produsentId = produsentId,
            kildeAppNavn = kildeAppNavn,
            eksterneVarsler = eksterneVarsler.map { it.tilHendelseModel(virksomhetsnummer) },
            påminnelse = paaminnelse?.tilDomene(
                notifikasjonOpprettetTidspunkt = opprettetTidspunkt,
                frist = frist,
                startTidspunkt = null,
                virksomhetsnummer = virksomhetsnummer,
            ),
            hardDelete = hardDelete?.tilHendelseModel(),
            frist = frist,
            sakId = sakId,
        )
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
    sealed interface NySakOgOppgaveResultat

    @JsonTypeName("NySakOgOppgaveVellykket")
    data class NySakOgOppgaveVellykket(
        val sakId: UUID,
        val oppgaveId: UUID,
        val eksterneVarsler: List<NyEksterntVarselResultat>,
        val paaminnelse: MutationNyOppgave.PåminnelseResultat?,
    ) : NySakOgOppgaveResultat
}

