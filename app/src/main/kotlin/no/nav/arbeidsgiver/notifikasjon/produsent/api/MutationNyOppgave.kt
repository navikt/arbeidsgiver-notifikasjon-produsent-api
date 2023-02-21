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
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentModel
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
                    env.notifikasjonContext(),
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
        fun tilDomene(
            id: UUID,
            produsentId: String,
            kildeAppNavn: String,
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
                mottakere = alleMottakere.map { it.tilDomene(metadata.virksomhetsnummer) },
                opprettetTidspunkt = metadata.opprettetTidspunkt,
                virksomhetsnummer = metadata.virksomhetsnummer,
                produsentId = produsentId,
                kildeAppNavn = kildeAppNavn,
                eksterneVarsler = eksterneVarsler.map {
                    it.tilDomene(metadata.virksomhetsnummer)
                },
                påminnelse = paaminnelse?.tilDomene(
                    opprettetTidspunkt = metadata.opprettetTidspunkt,
                    frist = frist,
                    virksomhetsnummer = metadata.virksomhetsnummer,
                ),
                hardDelete = metadata.hardDelete?.tilDomene(),
                frist = frist,
            )
        }
    }

    data class PaaminnelseInput(
        val tidspunkt: PaaminnelseTidspunktInput,
        val eksterneVarsler: List<PaaminnelseEksterntVarselInput>,
    ) {
        fun tilDomene(
            opprettetTidspunkt: OffsetDateTime,
            frist: LocalDate?,
            virksomhetsnummer: String,
        ) : HendelseModel.Påminnelse = HendelseModel.Påminnelse(
            tidspunkt = tidspunkt.tilDomene(opprettetTidspunkt, frist),
            eksterneVarsler = eksterneVarsler.map {
                it.tilDomene(virksomhetsnummer)
            }
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

    class PaaminnelseEksterntVarselInput(
        val sms: Sms?,
        val epost: Epost?,
    ) {
        fun tilDomene(virksomhetsnummer: String): HendelseModel.EksterntVarsel =
            when {
                sms != null -> sms.tilDomene(virksomhetsnummer)
                epost != null -> epost.tilDomene(virksomhetsnummer)
                else -> error("graphql-validation failed, neither sms nor epost defined")
            }

        class Sms(
            val mottaker: EksterntVarselInput.Sms.Mottaker,
            val smsTekst: String,
            val sendevindu: EksterntVarselInput.Sendevindu,
        ) {
            fun tilDomene(virksomhetsnummer: String): HendelseModel.SmsVarselKontaktinfo {
                if (mottaker.kontaktinfo != null) {
                    return HendelseModel.SmsVarselKontaktinfo(
                        varselId = UUID.randomUUID(),
                        tlfnr = mottaker.kontaktinfo.tlf,
                        fnrEllerOrgnr = virksomhetsnummer,
                        smsTekst = smsTekst,
                        sendevindu = sendevindu.somDomene,
                        sendeTidspunkt = null,
                    )
                }
                throw RuntimeException("mottaker-felt mangler for sms")
            }
        }
        class Epost(
            val mottaker: EksterntVarselInput.Epost.Mottaker,
            val epostTittel: String,
            val epostHtmlBody: String,
            val sendevindu: EksterntVarselInput.Sendevindu,
        ) {
            fun tilDomene(virksomhetsnummer: String): HendelseModel.EpostVarselKontaktinfo {
                if (mottaker.kontaktinfo != null) {
                    return HendelseModel.EpostVarselKontaktinfo(
                        varselId = UUID.randomUUID(),
                        epostAddr = mottaker.kontaktinfo.epostadresse,
                        fnrEllerOrgnr = virksomhetsnummer,
                        tittel = epostTittel,
                        htmlBody = epostHtmlBody,
                        sendevindu = sendevindu.somDomene,
                        sendeTidspunkt = null,
                    )
                }
                throw RuntimeException("mottaker mangler for epost")
            }
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
        val id = UUID.randomUUID()
        val domeneNyOppgave = try {
            nyOppgave.tilDomene(
                id = id,
                produsentId = produsent.id,
                kildeAppNavn = context.appName,
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
                    .also { println(it)}
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
                    "notifikasjon med angitt eksternId og merkelapp finnes fra før"
                )
            }
        }
    }
}

internal class UgyldigPåminnelseTidspunktException(message: String) : RuntimeException(message)