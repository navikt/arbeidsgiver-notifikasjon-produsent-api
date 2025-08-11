package no.nav.arbeidsgiver.notifikasjon.produsent.api

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnRessursMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnressursVarselKontaktinfo
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinntjenesteVarselKontaktinfo
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarsel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarselSendingsvindu
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EpostVarselKontaktinfo
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.Mottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.NærmesteLederMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SmsVarselKontaktinfo
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.ISO8601Period
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.Validators
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*

internal data class EksterntVarselInput(
    val sms: Sms?,
    val epost: Epost?,
    val altinntjeneste: Altinntjeneste?,
    val altinnressurs: Altinnressurs?,
) {
    init {
        Validators.ExactlyOneFieldGiven("EksterntVarselInput")(
            mapOf(
                "sms" to sms,
                "epost" to epost,
                "altinntjeneste" to altinntjeneste,
                "altinnressurs" to altinnressurs
            )
        )
    }

    fun tilHendelseModel(virksomhetsnummer: String): EksterntVarsel {
        if (sms != null) {
            return sms.tilHendelseModel(virksomhetsnummer)
        }
        if (epost != null) {
            return epost.tilHendelseModel(virksomhetsnummer)
        }
        if (altinntjeneste != null) {
            return altinntjeneste.tilHendelseModel(virksomhetsnummer)
        }
        if (altinnressurs != null) {
            return altinnressurs.tilHendelseModel(virksomhetsnummer)
        }
        throw RuntimeException("Feil format")
    }

    data class Sms(
        val mottaker: Mottaker,
        val smsTekst: String,
        val sendetidspunkt: SendetidspunktInput,
    ) {

        fun tilHendelseModel(virksomhetsnummer: String): SmsVarselKontaktinfo {
            val (sendevindu, sendeTidspunkt) = sendetidspunkt.somVinduOgTidspunkt()
            if (mottaker.kontaktinfo != null) {
                return SmsVarselKontaktinfo(
                    varselId = UUID.randomUUID(),
                    tlfnr = mottaker.kontaktinfo.tlf,
                    fnrEllerOrgnr = virksomhetsnummer,
                    smsTekst = smsTekst,
                    sendevindu = sendevindu,
                    sendeTidspunkt = sendeTidspunkt,
                )
            }
            throw RuntimeException("mottaker-felt mangler for sms")
        }

        data class Mottaker(
            val kontaktinfo: Kontaktinfo?,
        ) {
            init {
                Validators.ExactlyOneFieldGiven("sms.mottaker")(
                    mapOf(
                        "kontaktinfo" to kontaktinfo,
                    )
                )
            }
        }

        data class Kontaktinfo(
            val fnr: String?,
            val tlf: String,
        ) {
            init {
                Validators.compose(
                    Validators.NotBlank("Kontaktinfo.tlf"),
                    Validators.NorwegianMobilePhoneNumber("Kontaktinfo.tlf")
                )(tlf)
            }
        }
    }

    data class SendetidspunktInput(
        val tidspunkt: LocalDateTime?,
        val sendevindu: Sendevindu?,
    ) {
        init {
            Validators.ExactlyOneFieldGiven("SendetidspunktInput")(
                mapOf(
                    "tidspunkt" to tidspunkt,
                    "sendevindu" to sendevindu,
                )
            )
        }
    }

    @Suppress("unused") // kommer som graphql input
    enum class Sendevindu(
        val somDomene: EksterntVarselSendingsvindu,
    ) {
        NKS_AAPNINGSTID(EksterntVarselSendingsvindu.NKS_ÅPNINGSTID),
        DAGTID_IKKE_SOENDAG(EksterntVarselSendingsvindu.DAGTID_IKKE_SØNDAG),
        LOEPENDE(EksterntVarselSendingsvindu.LØPENDE),
    }

    data class Epost(
        val mottaker: Mottaker,
        val epostTittel: String,
        val epostHtmlBody: String,
        val sendetidspunkt: SendetidspunktInput,
    ) {
        fun tilHendelseModel(virksomhetsnummer: String): EpostVarselKontaktinfo {
            val (sendevindu, sendeTidspunkt) = sendetidspunkt.somVinduOgTidspunkt()
            if (mottaker.kontaktinfo != null) {
                return EpostVarselKontaktinfo(
                    varselId = UUID.randomUUID(),
                    epostAddr = mottaker.kontaktinfo.epostadresse,
                    fnrEllerOrgnr = virksomhetsnummer,
                    tittel = epostTittel,
                    htmlBody = epostHtmlBody,
                    sendevindu = sendevindu,
                    sendeTidspunkt = sendeTidspunkt
                )
            }
            throw RuntimeException("mottaker mangler for epost")
        }

        data class Mottaker(
            val kontaktinfo: Kontaktinfo?,
        ) {
            init {
                Validators.ExactlyOneFieldGiven("epost.mottaker")(
                    mapOf(
                        "kontaktinfo" to kontaktinfo,
                    )
                )
            }
        }

        data class Kontaktinfo(
            val fnr: String?,
            val epostadresse: String,
        ) {
            init {
                Validators.compose(
                    Validators.NotBlank("Kontaktinfo.epostadresse"),
                    Validators.Email("Kontaktinfo.epostadresse")
                )(epostadresse)
            }
        }
    }

    data class Altinntjeneste(
        val mottaker: Mottaker,
        val tittel: String,
        val innhold: String,
        val sendetidspunkt: SendetidspunktInput,
    ) {
        fun tilHendelseModel(virksomhetsnummer: String): AltinntjenesteVarselKontaktinfo {
            val (sendevindu, sendeTidspunkt) = sendetidspunkt.somVinduOgTidspunkt()
            return AltinntjenesteVarselKontaktinfo(
                varselId = UUID.randomUUID(),
                serviceCode = mottaker.serviceCode,
                serviceEdition = mottaker.serviceEdition,
                virksomhetsnummer = virksomhetsnummer,
                tittel = tittel.ensureSuffix(". "),
                innhold = innhold,
                sendevindu = sendevindu,
                sendeTidspunkt = sendeTidspunkt
            )
        }

        data class Mottaker(
            val serviceCode: String,
            val serviceEdition: String,
        )
    }

    data class Altinnressurs(
        val mottaker: Mottaker,
        val epostTittel: String,
        val epostHtmlBody: String,
        val smsTekst: String,
        val sendetidspunkt: SendetidspunktInput,
    ) {
        fun tilHendelseModel(virksomhetsnummer: String): AltinnressursVarselKontaktinfo {
            val (sendevindu, sendeTidspunkt) = sendetidspunkt.somVinduOgTidspunkt()
            return AltinnressursVarselKontaktinfo(
                varselId = UUID.randomUUID(),
                ressursId = mottaker.ressursId,
                virksomhetsnummer = virksomhetsnummer,
                epostTittel = epostTittel.ensureSuffix(". "),
                epostHtmlBody = epostHtmlBody,
                smsTekst = smsTekst,
                sendevindu = sendevindu,
                sendeTidspunkt = sendeTidspunkt
            )
        }

        data class Mottaker(
            val ressursId: String,
        ) {
            init {
                Validators.ExactlyOneFieldGiven("altinnressurs.mottaker")(
                    mapOf(
                        "ressursId" to ressursId,
                    )
                )
            }
        }
    }
}

internal data class NaermesteLederMottakerInput(
    val naermesteLederFnr: String,
    val ansattFnr: String,
) {
    fun tilDomene(virksomhetsnummer: String): Mottaker =
        NærmesteLederMottaker(
            naermesteLederFnr = naermesteLederFnr,
            ansattFnr = ansattFnr,
            virksomhetsnummer = virksomhetsnummer
        )
}

internal data class AltinnMottakerInput(
    val serviceCode: String,
    val serviceEdition: String,
) {
    fun tilDomene(virksomhetsnummer: String): Mottaker =
        AltinnMottaker(
            serviceCode = serviceCode,
            serviceEdition = serviceEdition,
            virksomhetsnummer = virksomhetsnummer
        )
}

internal data class AltinnRessursMottakerInput(
    val ressursId: String,
) {
    fun tilDomene(virksomhetsnummer: String): Mottaker =
        AltinnRessursMottaker(
            ressursId = ressursId,
            virksomhetsnummer = virksomhetsnummer
        )
}

internal class UkjentRolleException(message: String) : RuntimeException(message)


internal data class MottakerInput(
    val altinn: AltinnMottakerInput?,
    val naermesteLeder: NaermesteLederMottakerInput?,
    val altinnRessurs: AltinnRessursMottakerInput?
) {
    init {
        Validators.ExactlyOneFieldGiven("MottakerInput")(
            mapOf(
                "altinn" to altinn,
                "naermesteLeder" to naermesteLeder,
                "altinnRessurs" to altinnRessurs,
            )
        )
    }

    fun tilHendelseModel(
        virksomhetsnummer: String,
    ): Mottaker {
        check(listOfNotNull(altinn, naermesteLeder, altinnRessurs).size == 1) {
            "Ugyldig mottaker"
        }
        return altinnRessurs?.tilDomene(virksomhetsnummer)
            ?: altinn?.tilDomene(virksomhetsnummer)
            ?: naermesteLeder?.tilDomene(virksomhetsnummer)
            ?: throw IllegalArgumentException("Ugyldig mottaker")
    }
}

internal data class MetadataInput(
    val grupperingsid: String?,
    val eksternId: String,
    val opprettetTidspunkt: OffsetDateTime = OffsetDateTime.now(),
    val virksomhetsnummer: String,
    val hardDelete: FutureTemporalInput?,
)

internal data class NyEksterntVarselResultat(
    val id: UUID,
)

internal fun EksterntVarselInput.SendetidspunktInput.somVinduOgTidspunkt(): Pair<EksterntVarselSendingsvindu, LocalDateTime?> {
    val sendevindu = this.sendevindu?.somDomene ?: EksterntVarselSendingsvindu.SPESIFISERT

    if (sendevindu == EksterntVarselSendingsvindu.SPESIFISERT) {
        if (tidspunkt == null) {
            throw RuntimeException("SPESIFISERT sendevindu krever tidspunkt, men er null")
        } else {
            return Pair(sendevindu, this.tidspunkt)
        }
    }

    if (tidspunkt != null) {
        throw RuntimeException("$sendevindu bruker ikke tidspunkt, men det er oppgitt")
    } else {
        return Pair(sendevindu, null)
    }
}


internal data class PaaminnelseInput(
    val tidspunkt: PaaminnelseTidspunktInput,
    val eksterneVarsler: List<PaaminnelseEksterntVarselInput>,
) {
    fun tilDomene(
        notifikasjonOpprettetTidspunkt: OffsetDateTime,
        frist: LocalDate?,
        startTidspunkt: LocalDateTime?,
        virksomhetsnummer: String,
    ): HendelseModel.Påminnelse = HendelseModel.Påminnelse(
        tidspunkt = tidspunkt.tilDomene(notifikasjonOpprettetTidspunkt, frist, startTidspunkt),
        eksterneVarsler = eksterneVarsler.map {
            it.tilDomene(virksomhetsnummer)
        }
    )
}

data class PaaminnelseTidspunktInput(
    val konkret: LocalDateTime?,
    val etterOpprettelse: ISO8601Period?,
    val foerFrist: ISO8601Period?,
    val foerStartTidspunkt: ISO8601Period?,
) {
    init {
        Validators.ExactlyOneFieldGiven("PaaminnelseTidspunktInput")(
            mapOf(
                "konkret" to konkret,
                "etterOpprettelse" to etterOpprettelse,
                "foerFrist" to foerFrist,
                "foerStartTidspunkt" to foerStartTidspunkt,
            )
        )
    }
    fun tilDomene(
        notifikasjonOpprettetTidspunkt: OffsetDateTime,
        frist: LocalDate?,
        startTidspunkt: LocalDateTime?,
    ): HendelseModel.PåminnelseTidspunkt = when {
        konkret != null -> HendelseModel.PåminnelseTidspunkt.createAndValidateKonkret(
            konkret,
            notifikasjonOpprettetTidspunkt,
            frist,
            startTidspunkt,
        )

        etterOpprettelse != null -> HendelseModel.PåminnelseTidspunkt.createAndValidateEtterOpprettelse(
            etterOpprettelse,
            notifikasjonOpprettetTidspunkt,
            frist,
            startTidspunkt
        )

        foerFrist != null -> HendelseModel.PåminnelseTidspunkt.createAndValidateFørFrist(
            foerFrist,
            notifikasjonOpprettetTidspunkt,
            frist,
        )

        foerStartTidspunkt != null -> HendelseModel.PåminnelseTidspunkt.createAndValidateFørStartTidspunkt(
            foerStartTidspunkt,
            notifikasjonOpprettetTidspunkt,
            startTidspunkt,
        )

        else -> throw RuntimeException("Feil format")
    }
}

internal class PaaminnelseEksterntVarselInput(
    val sms: Sms?,
    val epost: Epost?,
    val altinntjeneste: Altinntjeneste?,
    val altinnressurs: Altinnressurs?,
) {
    init {
        Validators.ExactlyOneFieldGiven("PaaminnelseEksterntVarselInput")(
            mapOf(
                "sms" to sms,
                "epost" to epost,
                "altinntjeneste" to altinntjeneste,
                "altinnressurs" to altinnressurs,
            )
        )
    }

    fun tilDomene(virksomhetsnummer: String): EksterntVarsel =
        when {
            sms != null -> sms.tilDomene(virksomhetsnummer)
            epost != null -> epost.tilDomene(virksomhetsnummer)
            altinntjeneste != null -> altinntjeneste.tilDomene(virksomhetsnummer)
            altinnressurs != null -> altinnressurs.tilDomene(virksomhetsnummer)
            else -> error("graphql-validation failed, neither sms nor epost nor altinntjeneste defined")
        }

    class Sms(
        val mottaker: EksterntVarselInput.Sms.Mottaker,
        val smsTekst: String,
        val sendevindu: EksterntVarselInput.Sendevindu,
    ) {
        fun tilDomene(virksomhetsnummer: String): SmsVarselKontaktinfo {
            if (mottaker.kontaktinfo != null) {
                return SmsVarselKontaktinfo(
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
        fun tilDomene(virksomhetsnummer: String): EpostVarselKontaktinfo {
            if (mottaker.kontaktinfo != null) {
                return EpostVarselKontaktinfo(
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

    data class Altinntjeneste(
        val mottaker: Mottaker,
        val tittel: String,
        val innhold: String,
        val sendevindu: EksterntVarselInput.Sendevindu,
    ) {
        fun tilDomene(virksomhetsnummer: String): AltinntjenesteVarselKontaktinfo {
            return AltinntjenesteVarselKontaktinfo(
                varselId = UUID.randomUUID(),
                serviceCode = mottaker.serviceCode,
                serviceEdition = mottaker.serviceEdition,
                virksomhetsnummer = virksomhetsnummer,
                tittel = tittel.ensureSuffix(". "),
                innhold = innhold,
                sendevindu = sendevindu.somDomene,
                sendeTidspunkt = null
            )
        }

        data class Mottaker(
            val serviceCode: String,
            val serviceEdition: String,
        )
    }

    data class Altinnressurs(
        val mottaker: Mottaker,
        val epostTittel: String,
        val epostHtmlBody: String,
        val smsTekst: String,
        val sendevindu: EksterntVarselInput.Sendevindu,
    ) {
        fun tilDomene(virksomhetsnummer: String): AltinnressursVarselKontaktinfo {
            return AltinnressursVarselKontaktinfo(
                varselId = UUID.randomUUID(),
                ressursId = mottaker.ressursId,
                virksomhetsnummer = virksomhetsnummer,
                epostTittel = epostTittel.ensureSuffix(". "),
                epostHtmlBody = epostHtmlBody,
                smsTekst = smsTekst,
                sendevindu = sendevindu.somDomene,
                sendeTidspunkt = null
            )
        }

        data class Mottaker(
            val ressursId: String,
        )
    }
}
