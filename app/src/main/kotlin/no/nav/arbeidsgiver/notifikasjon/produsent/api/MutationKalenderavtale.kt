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
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentModel.Sak
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepository
import no.nav.arbeidsgiver.notifikasjon.produsent.api.MutationKalenderavtale.KalenderavtaleTilstand.AVLYST
import no.nav.arbeidsgiver.notifikasjon.produsent.api.MutationKalenderavtale.KalenderavtaleTilstand.VENTER_SVAR_FRA_ARBEIDSGIVER
import no.nav.arbeidsgiver.notifikasjon.produsent.tilProdusentModel
import java.time.Instant
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
        runtime.resolveSubtypes<OppdaterKalenderavtaleResultat>()

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
                        paaminnelse = env.getTypedArgumentOrNull<PaaminnelseInput>("paaminnelse"),
                        hardDelete = env.getTypedArgumentOrNull<FutureTemporalInput>("hardDelete"),
                    ),
                )
            }

            coDataFetcher("oppdaterKalenderavtale") { env ->
                kalenderavtaleOppdaterById(
                    context = env.notifikasjonContext(),
                    input = OppdaterByIdKalenderavtaleInput(
                        id = env.getTypedArgument<UUID>("id"),
                        nyTilstand = env.getTypedArgumentOrNull<KalenderavtaleTilstand>("nyTilstand"),
                        nyTekst = env.getTypedArgumentOrNull<String>("nyTekst"),
                        nyLenke = env.getTypedArgumentOrNull<String>("nyLenke"),
                        nyttStartTidspunkt = env.getTypedArgumentOrNull<LocalDateTime>("nyttStartTidspunkt"),
                        nyttSluttTidspunkt = env.getTypedArgumentOrNull<LocalDateTime>("nyttSluttTidspunkt"),
                        nyLokasjon = env.getTypedArgumentOrNull<NyKalenderavtaleInput.LokasjonInput?>("nyLokasjon"),
                        nyErDigitalt = env.getTypedArgumentOrNull<Boolean>("nyErDigitalt"),
                        eksterneVarsler = env.getTypedArgumentOrDefault<List<EksterntVarselInput>>("eksterneVarsler") { emptyList() }.ifEmpty { null },
                        paaminnelse = env.getTypedArgumentOrNull<PaaminnelseInput>("paaminnelse"),
                        hardDelete = env.getTypedArgumentOrNull<HardDeleteUpdateInput>("hardDelete"),
                        idempotenceKey = env.getTypedArgumentOrNull<String>("idempotenceKey"),
                    ),
                )
            }

            coDataFetcher("oppdaterKalenderavtaleByEksternId") { env ->
                oppdaterKalenderavtaleByEksternId(
                    context = env.notifikasjonContext(),
                    input = OppdaterByEksternIdKalenderavtaleInput(
                        merkelapp = env.getTypedArgument<String>("merkelapp"),
                        eksternId = env.getTypedArgument<String>("eksternId"),
                        nyTilstand = env.getTypedArgumentOrNull<KalenderavtaleTilstand>("nyTilstand"),
                        nyTekst = env.getTypedArgumentOrNull<String>("nyTekst"),
                        nyLenke = env.getTypedArgumentOrNull<String>("nyLenke"),
                        nyttStartTidspunkt = env.getTypedArgumentOrNull<LocalDateTime>("nyttStartTidspunkt"),
                        nyttSluttTidspunkt = env.getTypedArgumentOrNull<LocalDateTime>("nyttSluttTidspunkt"),
                        nyLokasjon = env.getTypedArgumentOrNull<NyKalenderavtaleInput.LokasjonInput?>("nyLokasjon"),
                        nyErDigitalt = env.getTypedArgumentOrNull<Boolean>("nyErDigitalt"),
                        eksterneVarsler = env.getTypedArgumentOrDefault<List<EksterntVarselInput>>("eksterneVarsler") { emptyList() }.ifEmpty { null },
                        paaminnelse = env.getTypedArgumentOrNull<PaaminnelseInput>("paaminnelse"),
                        hardDelete = env.getTypedArgumentOrNull<HardDeleteUpdateInput>("hardDelete"),
                        idempotenceKey = env.getTypedArgumentOrNull<String>("idempotenceKey"),
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
    sealed interface OppdaterKalenderavtaleResultat

    @JsonTypeName("OppdaterKalenderavtaleVellykket")
    data class OppdaterKalenderavtaleVellykket(
        val id: UUID,
    ) : OppdaterKalenderavtaleResultat

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
        val paaminnelse: PaaminnelseInput?,
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
            val opprettetTidspunkt = OffsetDateTime.now()
            return KalenderavtaleOpprettet(
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
                startTidspunkt = startTidspunkt,
                sluttTidspunkt = sluttTidspunkt,
                tilstand = tilstand.tilHendelseModel(),
                lokasjon = lokasjon?.tilHendelseModel(),
                erDigitalt = erDigitalt,
                hardDelete = hardDelete?.tilHendelseModel(),
                sakId = sakId,
                eksterneVarsler = eksterneVarsler.map { it.tilHendelseModel(virksomhetsnummer) },
                påminnelse = paaminnelse?.tilDomene(
                    notifikasjonOpprettetTidspunkt = opprettetTidspunkt,
                    frist = null,
                    startTidspunkt = startTidspunkt,
                    virksomhetsnummer = virksomhetsnummer,
                ),
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

    private suspend fun nyKalenderavtale(
        context: ProdusentAPI.Context,
        nyKalenderavtale: NyKalenderavtaleInput,
    ): NyKalenderavtaleResultat {
        val produsent = hentProdusent(context) { error -> return error }
        val sak: Sak = nyKalenderavtale.grupperingsid.let { grupperingsid ->
            hentSak(
                produsentRepository = produsentRepository,
                grupperingsid = grupperingsid,
                merkelapp = nyKalenderavtale.merkelapp
            ) { error -> return error }
        }

        val id = UUID.randomUUID()
        val nyKalenderavtaleHendelse = nyKalenderavtale.somKalenderavtaleOpprettetHendelse(
            id = id,
            produsentId = produsent.id,
            kildeAppNavn = context.appName,
            sakId = sak.id,
        ) { error -> return error }

        tilgangsstyrNyNotifikasjon(
            produsent,
            nyKalenderavtaleHendelse.mottakere,
            nyKalenderavtale.merkelapp
        ) { error -> return error }

        validerMottakereMotSak(sak, nyKalenderavtale.virksomhetsnummer, nyKalenderavtale.mottakere) { error ->
            return error
        }

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


    sealed class OppdaterKalenderavtaleInput {
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
                eksterneVarsler = eksterneVarsler?.map { it.tilHendelseModel(eksisterende.virksomhetsnummer) } ?: emptyList(),
                påminnelse = if (nyTilstand == AVLYST) null else paaminnelse?.tilDomene(
                    notifikasjonOpprettetTidspunkt = eksisterende.opprettetTidspunkt,
                    frist = null,
                    startTidspunkt = nyttStartTidspunkt ?: eksisterende.startTidspunkt,
                    virksomhetsnummer = eksisterende.virksomhetsnummer,
                ),
                idempotenceKey = idempotenceKey,
                oppdatertTidspunkt = Instant.now(),
                opprettetTidspunkt = eksisterende.opprettetTidspunkt.toInstant(),
                merkelapp = eksisterende.merkelapp,
                grupperingsid = eksisterende.grupperingsid,
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
        abstract val eksterneVarsler: List<EksterntVarselInput>?
        abstract val paaminnelse: PaaminnelseInput?
        abstract val idempotenceKey: String?

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
                eksterneVarsler,
                idempotenceKey,
            ).isEmpty()
    }

    data class OppdaterByIdKalenderavtaleInput(
        val id: UUID,
        override val nyTilstand: KalenderavtaleTilstand?,
        override val nyTekst: String?,
        override val nyLenke: String?,
        override val nyttStartTidspunkt: LocalDateTime?,
        override val nyttSluttTidspunkt: LocalDateTime?,
        override val nyLokasjon: NyKalenderavtaleInput.LokasjonInput?,
        override val nyErDigitalt: Boolean?,
        override val eksterneVarsler: List<EksterntVarselInput>?,
        override val paaminnelse: PaaminnelseInput?,
        override val hardDelete: HardDeleteUpdateInput?,
        override val idempotenceKey: String?,
    ) : OppdaterKalenderavtaleInput()

    data class OppdaterByEksternIdKalenderavtaleInput(
        val eksternId: String,
        val merkelapp: String,
        override val nyTilstand: KalenderavtaleTilstand?,
        override val nyTekst: String?,
        override val nyLenke: String?,
        override val nyttStartTidspunkt: LocalDateTime?,
        override val nyttSluttTidspunkt: LocalDateTime?,
        override val nyLokasjon: NyKalenderavtaleInput.LokasjonInput?,
        override val nyErDigitalt: Boolean?,
        override val eksterneVarsler: List<EksterntVarselInput>?,
        override val paaminnelse: PaaminnelseInput?,
        override val hardDelete: HardDeleteUpdateInput?,
        override val idempotenceKey: String?,
    ) : OppdaterKalenderavtaleInput()

    private suspend fun kalenderavtaleOppdaterById(
        context: ProdusentAPI.Context,
        input: OppdaterByIdKalenderavtaleInput,
    ): OppdaterKalenderavtaleResultat {
        val produsent = hentProdusent(context) { error -> return error }

        val eksisterende = produsentRepository.hentNotifikasjon(input.id)?.let {
            it as? ProdusentModel.Kalenderavtale
        } ?: return Error.NotifikasjonFinnesIkke("kalenderavtale med id=${input.id} finnes ikke")

        tilgangsstyrMerkelapp(produsent, eksisterende.merkelapp) { error -> return error }

        return oppdaterKalenderavtale(
            context.appName,
            produsent.id,
            eksisterende,
            input
        )
    }

    private suspend fun oppdaterKalenderavtaleByEksternId(
        context: ProdusentAPI.Context,
        input: OppdaterByEksternIdKalenderavtaleInput,
    ): OppdaterKalenderavtaleResultat {
        val produsent = hentProdusent(context) { error -> return error }

        val eksisterende = produsentRepository.hentNotifikasjon(
            eksternId = input.eksternId,
            merkelapp = input.merkelapp
        )?.let {
            it as? ProdusentModel.Kalenderavtale
        }
            ?: return Error.NotifikasjonFinnesIkke("kalenderavtale med eksternId=${input.eksternId} og merkelapp=${input.merkelapp} finnes ikke")

        tilgangsstyrMerkelapp(produsent, eksisterende.merkelapp) { error -> return error }

        return oppdaterKalenderavtale(
            context.appName,
            produsent.id,
            eksisterende,
            input
        )
    }

    private suspend fun oppdaterKalenderavtale(
        kildeAppNavn: String,
        produsentId: String,
        eksisterende: ProdusentModel.Kalenderavtale,
        input: OppdaterKalenderavtaleInput,
    ): OppdaterKalenderavtaleResultat {
        if (input.isNoOp) {
            return OppdaterKalenderavtaleVellykket(
                id = eksisterende.id,
            )
        }

        input.idempotenceKey?.let {
            val oppdateringFinnes = produsentRepository.notifikasjonOppdateringFinnes(
                eksisterende.id,
                input.idempotenceKey!!
            )
            if (oppdateringFinnes) {
                return OppdaterKalenderavtaleVellykket(
                    id = eksisterende.id,
                )
            }
        }

        val hendelse = input.somKalenderavtaleOppdatertHendelse(
            kildeAppNavn,
            produsentId,
            eksisterende,
        ) { error -> return error }

        hendelseDispatcher.send(hendelse)
        return OppdaterKalenderavtaleVellykket(
            id = eksisterende.id,
        )
    }
}


