package no.nav.arbeidsgiver.notifikasjon.produsent.api

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import graphql.schema.idl.RuntimeWiring
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.KalenderavtaleOppdatert
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.KalenderavtaleOpprettet
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentModel
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepository
import no.nav.arbeidsgiver.notifikasjon.produsent.api.MutationKalenderavtale.NyKalenderavtaleInput.KalenderavtaleTilstand
import no.nav.arbeidsgiver.notifikasjon.produsent.api.MutationKalenderavtale.NyKalenderavtaleInput.KalenderavtaleTilstand.VENTER_SVAR_FRA_ARBEIDSGIVER
import no.nav.arbeidsgiver.notifikasjon.produsent.tilProdusentModel
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*

internal class MutationKalenderavtale(
    private val hendelseDispatcher: HendelseDispatcher,
    private val produsentRepository: ProdusentRepository,
) {
    private val log = logger()

    fun wire(runtime: RuntimeWiring.Builder) {
        runtime.resolveSubtypes<NyKalenderavtaleResultat>()
        runtime.resolveSubtypes<KalenderavtaleOppdaterResultat>()

        runtime.wire("Mutation") {
            coDataFetcher("nyKalenderavtale") { env ->
                nyKalenderavtale(
                    context = env.notifikasjonContext(),
                    nyKalenderavtale = NyKalenderavtaleInput(
                        virksomhetsnummer = env.getTypedArgument<String>("virksomhetsnummer"),
                        grupperingsid = env.getTypedArgument<String>("grupperingsid"),
                        merkelapp = env.getTypedArgument<String>("merkelapp"),
                        eksternId = env.getTypedArgument<String>("eksternId"),
                        tekst = env.getTypedArgument<String>("tekst"),
                        lenke = env.getTypedArgument<String>("lenke"),
                        mottakere = env.getTypedArgument<List<MottakerInput>>("mottakere"),
                        startTidspunkt = env.getTypedArgument<LocalDateTime>("startTidspunkt"),
                        sluttTidspunkt = env.getTypedArgumentOrNull<LocalDateTime>("sluttTidspunkt"),
                        lokasjon = env.getTypedArgumentOrNull<NyKalenderavtaleInput.LokasjonInput?>("lokasjon"),
                        erDigitalt = env.getTypedArgumentOrNull<Boolean>("erDigitalt") ?: false,
                        tilstand = env.getTypedArgumentOrDefault<KalenderavtaleTilstand>("tilstand") { VENTER_SVAR_FRA_ARBEIDSGIVER },
                        eksterneVarsler = env.getTypedArgumentOrDefault<List<EksterntVarselInput>>("eksterneVarsler") { emptyList() },
                        hardDelete = env.getTypedArgumentOrNull<FutureTemporalInput>("hardDelete"),
                    ),
                )
            }

            coDataFetcher("kalenderavtaleOppdater") { env ->
                kalenderavtaleOppdaterById(
                    context = env.notifikasjonContext(),
                    input = KalenderavtaleOppdaterByIdInput(
                        id = env.getTypedArgument<UUID>("id"),
                        nyTilstand = env.getTypedArgumentOrNull<KalenderavtaleTilstand>("nyTilstand"),
                        nyTekst = env.getTypedArgumentOrNull<String>("nyTekst"),
                        nyLenke = env.getTypedArgumentOrNull<String>("nyLenke"),
                        nyttStartTidspunkt = env.getTypedArgumentOrNull<LocalDateTime>("nyttStartTidspunkt"),
                        nyttSluttTidspunkt = env.getTypedArgumentOrNull<LocalDateTime>("nyttSluttTidspunkt"),
                        nyLokasjon = env.getTypedArgumentOrNull<NyKalenderavtaleInput.LokasjonInput?>("nyLokasjon"),
                        nyErDigitalt = env.getTypedArgumentOrNull<Boolean>("nyErDigitalt"),
                        hardDelete = env.getTypedArgumentOrNull<HardDeleteUpdateInput>("hardDelete"),
                    ),
                )
            }

            coDataFetcher("kalenderavtaleOppdaterByEksternId") { env ->
                kalenderavtaleOppdaterByEksternId(
                    context = env.notifikasjonContext(),
                    input = KalenderavtaleOppdaterByEksternIdInput(
                        merkelapp = env.getTypedArgument<String>("merkelapp"),
                        eksternId = env.getTypedArgument<String>("eksternId"),
                        nyTilstand = env.getTypedArgumentOrNull<KalenderavtaleTilstand>("nyTilstand"),
                        nyTekst = env.getTypedArgumentOrNull<String>("nyTekst"),
                        nyLenke = env.getTypedArgumentOrNull<String>("nyLenke"),
                        nyttStartTidspunkt = env.getTypedArgumentOrNull<LocalDateTime>("nyttStartTidspunkt"),
                        nyttSluttTidspunkt = env.getTypedArgumentOrNull<LocalDateTime>("nyttSluttTidspunkt"),
                        nyLokasjon = env.getTypedArgumentOrNull<NyKalenderavtaleInput.LokasjonInput?>("nyLokasjon"),
                        nyErDigitalt = env.getTypedArgumentOrNull<Boolean>("nyErDigitalt"),
                        hardDelete = env.getTypedArgumentOrNull<HardDeleteUpdateInput>("hardDelete"),
                    ),
                )
            }
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
    sealed interface NyKalenderavtaleResultat

    @JsonTypeName("NyKalenderavtaleVellykket")
    data class NyKalenderavtaleVellykket(
        val id: UUID,
        val eksterneVarsler: List<NyEksterntVarselResultat>
    ) : NyKalenderavtaleResultat

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
    sealed interface KalenderavtaleOppdaterResultat

    @JsonTypeName("KalenderavtaleOppdaterVellykket")
    data class KalenderavtaleOppdaterVellykket(
        val id: UUID,
    ) : KalenderavtaleOppdaterResultat

    data class NyKalenderavtaleInput(
        val virksomhetsnummer: String,
        val grupperingsid: String,
        val merkelapp: String,
        val eksternId: String,
        val tekst: String,
        val lenke: String,
        val mottakere: List<MottakerInput>,
        val startTidspunkt: LocalDateTime,
        val sluttTidspunkt: LocalDateTime?,
        val lokasjon: LokasjonInput?,
        val erDigitalt: Boolean,
        val tilstand: KalenderavtaleTilstand,
        val eksterneVarsler: List<EksterntVarselInput>,
        val hardDelete: FutureTemporalInput?,
    ) {
        inline fun somKalenderavtaleOpprettetHendelse(
            id: UUID,
            produsentId: String,
            kildeAppNavn: String,
            sakId: UUID,
            onValidationError: (error: Error.UgyldigKalenderavtale) -> Nothing
        ): KalenderavtaleOpprettet {
            if (sluttTidspunkt != null && sluttTidspunkt.isBefore(startTidspunkt)) {
                onValidationError(
                    Error.UgyldigKalenderavtale(
                        "sluttTidspunkt kan ikke være før startTidspunkt"
                    )
                )
            }
            return KalenderavtaleOpprettet(
                hendelseId = id,
                notifikasjonId = id,
                merkelapp = merkelapp,
                tekst = tekst,
                grupperingsid = grupperingsid,
                lenke = lenke,
                eksternId = eksternId,
                mottakere = mottakere.map { it.tilHendelseModel(virksomhetsnummer) },
                opprettetTidspunkt = OffsetDateTime.now(),
                virksomhetsnummer = virksomhetsnummer,
                produsentId = produsentId,
                kildeAppNavn = kildeAppNavn,
                startTidspunkt = startTidspunkt,
                sluttTidspunkt = sluttTidspunkt,
                tilstand = tilstand.tilHendelseModel(),
                lokasjon = lokasjon?.tilHendelseModel(),
                erDigitalt = erDigitalt,
                hardDelete = hardDelete?.tilHendelseModel(),
                sakId = sakId,
                eksterneVarsler = eksterneVarsler.map { it.tilDomene(virksomhetsnummer) },
                påminnelse = null,
            )
        }

        data class LokasjonInput(
            val adresse: String,
            val postnummer: String,
            val poststed: String,
        ) {
            fun tilHendelseModel() = HendelseModel.Lokasjon(
                adresse = adresse,
                postnummer = postnummer,
                poststed = poststed,
            )
        }

        enum class KalenderavtaleTilstand {
            VENTER_SVAR_FRA_ARBEIDSGIVER,
            ARBEIDSGIVER_VIL_AVLYSE,
            ARBEIDSGIVER_VIL_ENDRE_TID_ELLER_STED,
            ARBEIDSGIVER_HAR_GODTATT,
            AVLYST;

            fun tilHendelseModel(): HendelseModel.KalenderavtaleTilstand = when (this) {
                VENTER_SVAR_FRA_ARBEIDSGIVER -> HendelseModel.KalenderavtaleTilstand.VENTER_SVAR_FRA_ARBEIDSGIVER
                ARBEIDSGIVER_VIL_AVLYSE -> HendelseModel.KalenderavtaleTilstand.ARBEIDSGIVER_VIL_AVLYSE
                ARBEIDSGIVER_VIL_ENDRE_TID_ELLER_STED -> HendelseModel.KalenderavtaleTilstand.ARBEIDSGIVER_VIL_ENDRE_TID_ELLER_STED
                ARBEIDSGIVER_HAR_GODTATT -> HendelseModel.KalenderavtaleTilstand.ARBEIDSGIVER_HAR_GODTATT
                AVLYST -> HendelseModel.KalenderavtaleTilstand.AVLYST
            }
        }
    }

    private suspend fun nyKalenderavtale(
        context: ProdusentAPI.Context,
        nyKalenderavtale: NyKalenderavtaleInput,
    ): NyKalenderavtaleResultat {
        val produsent = hentProdusent(context) { error -> return error }
        val sakId: UUID = nyKalenderavtale.grupperingsid.let { grupperingsid ->
            hentSak(
                produsentRepository = produsentRepository,
                grupperingsid = grupperingsid,
                merkelapp = nyKalenderavtale.merkelapp
            ) { error -> return error }.id
        }

        val id = UUID.randomUUID()
        val nyKalenderavtaleHendelse = nyKalenderavtale.somKalenderavtaleOpprettetHendelse(
            id = id,
            produsentId = produsent.id,
            kildeAppNavn = context.appName,
            sakId = sakId,
        ) { error -> return error }

        tilgangsstyrNyNotifikasjon(
            produsent,
            nyKalenderavtaleHendelse.mottakere,
            nyKalenderavtale.merkelapp
        ) { error -> return error }

        val eksisterende = produsentRepository.hentNotifikasjon(
            eksternId = nyKalenderavtaleHendelse.eksternId,
            merkelapp = nyKalenderavtaleHendelse.merkelapp
        )

        return when {
            eksisterende == null -> {
                log.info("oppretter ny kalenderavtale med id $id")
                hendelseDispatcher.send(nyKalenderavtaleHendelse)
                NyKalenderavtaleVellykket(
                    id = id,
                    eksterneVarsler = nyKalenderavtaleHendelse.eksterneVarsler.map {
                        NyEksterntVarselResultat(it.varselId)
                    }
                )
            }

            eksisterende.erDuplikatAv(nyKalenderavtaleHendelse.tilProdusentModel()) -> {
                log.info("duplisert opprettelse av kalenderavtale med id ${eksisterende.id}")
                NyKalenderavtaleVellykket(
                    id = eksisterende.id,
                    eksterneVarsler = eksisterende.eksterneVarsler.map {
                        NyEksterntVarselResultat(it.varselId)
                    }
                )
            }

            else -> {
                Error.DuplikatEksternIdOgMerkelapp(
                    "notifikasjon med angitt eksternId og merkelapp finnes fra før",
                    eksisterende.id
                )
            }
        }
    }


    sealed class KalenderavtaleOppdaterInput {
        inline fun somKalenderavtaleOppdatertHendelse(
            kildeAppNavn: String,
            produsentId: String,
            eksisterende: ProdusentModel.Kalenderavtale,
            onValidationError: (error: Error.UgyldigKalenderavtale) -> Nothing,
        ): KalenderavtaleOppdatert {

            if (
                nyttStartTidspunkt != null &&
                nyttSluttTidspunkt != null &&
                nyttStartTidspunkt!!.isAfter(nyttSluttTidspunkt)
            ) {
                onValidationError(
                    Error.UgyldigKalenderavtale("startTidspunkt må være før sluttTidspunkt")
                )
            } else if (
                nyttStartTidspunkt != null &&
                nyttStartTidspunkt!!.isAfter(eksisterende.sluttTidspunkt)
            ) {
                onValidationError(
                    Error.UgyldigKalenderavtale("startTidspunkt må være før sluttTidspunkt")
                )
            } else if (
                nyttSluttTidspunkt != null
                && nyttSluttTidspunkt!!.isBefore(eksisterende.startTidspunkt)
            ) {
                onValidationError(
                    Error.UgyldigKalenderavtale("startTidspunkt må være før sluttTidspunkt")
                )
            }

            return KalenderavtaleOppdatert(
                virksomhetsnummer = eksisterende.virksomhetsnummer,
                hendelseId = UUID.randomUUID(),
                produsentId = produsentId,
                kildeAppNavn = kildeAppNavn,
                notifikasjonId = eksisterende.id,

                tilstand = nyTilstand?.tilHendelseModel(),
                lenke = nyLenke,
                tekst = nyTekst,
                startTidspunkt = nyttStartTidspunkt,
                sluttTidspunkt = nyttSluttTidspunkt,
                lokasjon = nyLokasjon?.tilHendelseModel(),
                erDigitalt = nyErDigitalt,
                hardDelete = hardDelete?.tilHendelseModel(),
                eksterneVarsler = emptyList(),
                påminnelse = null,
            )
        }

        abstract val nyTilstand: KalenderavtaleTilstand?
        abstract val nyTekst: String?
        abstract val nyLenke: String?
        abstract val nyttStartTidspunkt: LocalDateTime?
        abstract val nyttSluttTidspunkt: LocalDateTime?
        abstract val nyLokasjon: NyKalenderavtaleInput.LokasjonInput?
        abstract val nyErDigitalt: Boolean?
        abstract val hardDelete: HardDeleteUpdateInput?

        /**
         * siden alle verdier er optional mtp forward compat:
         * hvis kallet ikke kan medføre endring anses det som vellykket.
         */
        val isNoOp: Boolean
            get() = listOfNotNull(
                nyTilstand,
                nyTekst,
                nyLenke,
                nyttStartTidspunkt,
                nyttSluttTidspunkt,
                nyLokasjon,
                nyErDigitalt,
                hardDelete,
            ).isEmpty()
    }

    data class KalenderavtaleOppdaterByIdInput(
        val id: UUID,
        override val nyTilstand: KalenderavtaleTilstand?,
        override val nyTekst: String?,
        override val nyLenke: String?,
        override val nyttStartTidspunkt: LocalDateTime?,
        override val nyttSluttTidspunkt: LocalDateTime?,
        override val nyLokasjon: NyKalenderavtaleInput.LokasjonInput?,
        override val nyErDigitalt: Boolean?,
        override val hardDelete: HardDeleteUpdateInput?,
    ) : KalenderavtaleOppdaterInput()

    data class KalenderavtaleOppdaterByEksternIdInput(
        val eksternId: String,
        val merkelapp: String,
        override val nyTilstand: KalenderavtaleTilstand?,
        override val nyTekst: String?,
        override val nyLenke: String?,
        override val nyttStartTidspunkt: LocalDateTime?,
        override val nyttSluttTidspunkt: LocalDateTime?,
        override val nyLokasjon: NyKalenderavtaleInput.LokasjonInput?,
        override val nyErDigitalt: Boolean?,
        override val hardDelete: HardDeleteUpdateInput?,
    ) : KalenderavtaleOppdaterInput()

    private suspend fun kalenderavtaleOppdaterById(
        context: ProdusentAPI.Context,
        input: KalenderavtaleOppdaterByIdInput,
    ): KalenderavtaleOppdaterResultat {
        val produsent = hentProdusent(context) { error -> return error }

        val eksisterende = produsentRepository.hentNotifikasjon(input.id)?.let {
            it as? ProdusentModel.Kalenderavtale
        } ?: return Error.NotifikasjonFinnesIkke("kalenderavtale med id=${input.id} finnes ikke")

        tilgangsstyrMerkelapp(produsent, eksisterende.merkelapp) { error -> return error }

        return kalenderavtaleOppdater(
            context.appName,
            produsent.id,
            eksisterende,
            input
        )
    }

    private suspend fun kalenderavtaleOppdaterByEksternId(
        context: ProdusentAPI.Context,
        input: KalenderavtaleOppdaterByEksternIdInput,
    ): KalenderavtaleOppdaterResultat {
        val produsent = hentProdusent(context) { error -> return error }

        val eksisterende = produsentRepository.hentNotifikasjon(
            eksternId = input.eksternId,
            merkelapp = input.merkelapp
        )?.let {
            it as? ProdusentModel.Kalenderavtale
        }
            ?: return Error.NotifikasjonFinnesIkke("kalenderavtale med eksternId=${input.eksternId} og merkelapp=${input.merkelapp} finnes ikke")

        tilgangsstyrMerkelapp(produsent, eksisterende.merkelapp) { error -> return error }

        return kalenderavtaleOppdater(
            context.appName,
            produsent.id,
            eksisterende,
            input
        )
    }

    private suspend fun kalenderavtaleOppdater(
        kildeAppNavn: String,
        produsentId: String,
        eksisterende: ProdusentModel.Kalenderavtale,
        input: KalenderavtaleOppdaterInput,
    ): KalenderavtaleOppdaterResultat {
        if (input.isNoOp) {
            return KalenderavtaleOppdaterVellykket(
                id = eksisterende.id,
            )
        }

        val hendelse = input.somKalenderavtaleOppdatertHendelse(
            kildeAppNavn,
            produsentId,
            eksisterende,
        ) { error -> return error }

        hendelseDispatcher.send(hendelse)
        return KalenderavtaleOppdaterVellykket(
            id = eksisterende.id,
        )
    }
}


