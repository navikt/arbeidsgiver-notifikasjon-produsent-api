package no.nav.arbeidsgiver.notifikasjon.produsent.api

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import graphql.schema.idl.RuntimeWiring
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.Mottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.NyStatusSak
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.NærmesteLederMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SakOpprettet
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.basedOnEnv
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentModel
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepository
import java.lang.RuntimeException
import java.time.OffsetDateTime
import java.util.*

internal class MutationNySak(
    private val hendelseDispatcher: HendelseDispatcher,
    private val produsentRepository: ProdusentRepository,
) {
    private val log = logger()

    fun wire(runtime: RuntimeWiring.Builder) {
        runtime.resolveSubtypes<NySakResultat>()

        runtime.wire("Mutation") {
            coDataFetcher("nySak") { env ->
                nySak(
                    context = env.notifikasjonContext(),
                    nySak = NySakInput(
                        grupperingsid = env.getTypedArgument("grupperingsid"),
                        merkelapp = env.getTypedArgument("merkelapp"),
                        virksomhetsnummer = env.getTypedArgument("virksomhetsnummer"),
                        mottakere = env.getTypedArgument("mottakere"),
                        tittel = env.getTypedArgument("tittel"),
                        lenke = env.getTypedArgument("lenke"),
                        status = SaksStatusInput(
                            status = env.getTypedArgument("initiellStatus"),
                            tidspunkt = env.getTypedArgumentOrNull("tidspunkt"),
                            overstyrStatustekstMed = env.getTypedArgumentOrNull("overstyrStatustekstMed"),
                        ),
                        hardDelete = env.getTypedArgumentOrNull("hardDelete"),
                    )
                )
            }
        }
    }

    private suspend fun nySak(
        context: ProdusentAPI.Context,
        nySak: NySakInput,
    ): NySakResultat {
        val produsent = hentProdusent(context) { error -> return error }
        val sakId = UUID.randomUUID()
        val mottattTidspunkt = OffsetDateTime.now()

        val sakOpprettetHendelse = try {
            nySak.somSakOpprettetHendelse(
                id = sakId,
                produsentId = produsent.id,
                kildeAppNavn = context.appName,
                mottattTidspunkt = mottattTidspunkt,
            )
        } catch (e: UkjentRolleException) {
            return Error.UkjentRolle(e.message!!)
        }

        val statusoppdateringHendelse = nySak.somNyStatusSakHendelse(
            hendelseId = UUID.randomUUID(),
            sakId = sakId,
            produsentId = produsent.id,
            kildeAppNavn = context.appName,
            mottattTidspunkt = mottattTidspunkt,
        )

        tilgangsstyrNyNotifikasjon(
            produsent,
            sakOpprettetHendelse.mottakere,
            sakOpprettetHendelse.merkelapp,
        ) { error -> return error }

        val eksisterende = produsentRepository.hentSak(
            grupperingsid = sakOpprettetHendelse.grupperingsid,
            merkelapp = sakOpprettetHendelse.merkelapp,
        )

        return when {
            eksisterende == null -> {
                log.info("oppretter ny sak med id $sakId")
                hendelseDispatcher.send(sakOpprettetHendelse, statusoppdateringHendelse)
                check(produsentRepository.hentSak(sakId) != null) {
                    """
                        Sak med id $sakId ble produsert til kafka men ble ikke lagret i produsent-databasen. 
                        Dette er sannsynligvis en race condition to saker med samme koordinat opprettet samtidig.
                    """
                }

                NySakVellykket(
                    id = sakId,
                )
            }
            nySak.erDuplikatAv(eksisterende) -> {
                if (eksisterende.statusoppdateringRegistrert()) {
                    log.info("duplisert opprettelse av sak med id ${eksisterende.id}")
                } else {
                    log.info("statusoppdatering ikke registrert for duplisert opprettelse av sak med id ${eksisterende.id}")
                    hendelseDispatcher.send(statusoppdateringHendelse.copy(sakId = eksisterende.id))
                }

                NySakVellykket(
                    id = eksisterende.id,
                )
            }
            else -> {
                Error.DuplikatGrupperingsid(
                    "sak med angitt grupperings-id og merkelapp finnes fra før"
                )
            }
        }
    }

    data class NySakInput(
        val grupperingsid: String,
        val merkelapp: String,
        val virksomhetsnummer: String,
        val mottakere: List<MottakerInput>,
        val tittel: String,
        val lenke: String,
        val status: SaksStatusInput,
        val hardDelete: FutureTemporalInput?,
    ) {
        fun somSakOpprettetHendelse(
            id: UUID,
            produsentId: String,
            kildeAppNavn: String,
            mottattTidspunkt: OffsetDateTime,
        ) = SakOpprettet(
            hendelseId = id,
            virksomhetsnummer = virksomhetsnummer,
            produsentId = produsentId,
            kildeAppNavn = kildeAppNavn,
            sakId = id,
            grupperingsid = grupperingsid,
            merkelapp = merkelapp,
        mottakere = mottakere.map { it.tilDomene(virksomhetsnummer) },
            tittel = tittel,
            lenke = lenke,
            oppgittTidspunkt = status.tidspunkt,
            mottattTidspunkt = mottattTidspunkt,
            hardDelete = hardDelete?.tilDomene(),
        )

        fun somNyStatusSakHendelse(
            hendelseId: UUID,
            sakId: UUID,
            produsentId: String,
            kildeAppNavn: String,
            mottattTidspunkt: OffsetDateTime,
        ) = NyStatusSak(
            hendelseId = hendelseId,
            virksomhetsnummer = virksomhetsnummer,
            produsentId = produsentId,
            kildeAppNavn = kildeAppNavn,
            sakId = sakId,
            status = status.status.hendelseType,
            overstyrStatustekstMed = status.overstyrStatustekstMed,
            oppgittTidspunkt = status.tidspunkt,
            mottattTidspunkt = mottattTidspunkt,
            idempotensKey = IdempotenceKey.initial(),
            hardDelete = null,
            nyLenkeTilSak = null,
        )
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
    sealed interface NySakResultat

    @JsonTypeName("NySakVellykket")
    data class NySakVellykket(
        val id: UUID,
    ) : NySakResultat
}

internal fun MutationNySak.NySakInput.erDuplikatAv(eksisterende: ProdusentModel.Sak): Boolean {
    val initialOppdatering = eksisterende.statusoppdateringer.find {
        it.idempotencyKey == IdempotenceKey.initial()
    }

    return this.virksomhetsnummer == eksisterende.virksomhetsnummer &&
            this.merkelapp == eksisterende.merkelapp &&
            this.grupperingsid == eksisterende.grupperingsid &&
            this.tittel == eksisterende.tittel &&
            this.lenke == eksisterende.lenke &&
            this.mottakere.equalsAsSets(eksisterende.mottakere, MottakerInput::sammeSom) &&
            (initialOppdatering == null || this.status.isDuplicateOf(initialOppdatering))
}


private fun MottakerInput.sammeSom(mottaker: Mottaker): Boolean {
    return when (mottaker) {
        is AltinnMottaker ->
            mottaker.serviceCode == this.altinn?.serviceCode &&
                    mottaker.serviceEdition == this.altinn.serviceEdition
        is NærmesteLederMottaker ->
            mottaker.ansattFnr == this.naermesteLeder?.ansattFnr &&
                    mottaker.naermesteLederFnr == this.naermesteLeder.naermesteLederFnr
        is HendelseModel._AltinnRolleMottaker -> basedOnEnv(
            prod = { throw RuntimeException("AltinnRolleMottaker støttes ikke i prod") },
            other = { false },
        )
        is HendelseModel._AltinnReporteeMottaker -> basedOnEnv(
            prod = { throw RuntimeException("AltinnReporteeMottaker støttes ikke i prod") },
            other = { false },
        )
    }
}

private fun <S, T> List<S>.subsetOf(other: List<T>, equals: (S, T) -> Boolean) =
    this.all { x -> other.any { y -> equals(x, y) } }

private fun <S, T> List<S>.equalsAsSets(other: List<T>, equals: (S, T) -> Boolean) =
    this.subsetOf(other, equals) && other.subsetOf(this) { x, y -> equals(y, x) }


