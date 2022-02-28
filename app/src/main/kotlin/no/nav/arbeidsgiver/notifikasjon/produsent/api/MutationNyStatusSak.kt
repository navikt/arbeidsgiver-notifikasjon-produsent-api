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
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.sendHendelse
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentModel
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepository
import java.time.OffsetDateTime
import java.util.*

class MutationNyStatusSak(
    private val kafkaProducer: CoroutineKafkaProducer<KafkaKey, Hendelse>,
    private val produsentRepository: ProdusentRepository,
) {

    fun wire(runtime: RuntimeWiring.Builder) {
        runtime.resolveSubtypes<NyStatusSakResultat>()

        runtime.wire("Mutation") {
            coDataFetcher("nyStatusSak") { env ->
                nyStatusSak(
                    context = env.getContext(),
                    id = env.getTypedArgument("id"),
                    status = env.getTypedArgument("status"),
                )
            }
            coDataFetcher("nyStatusSakByGrupperingsid") { env ->
                nyStatusSakByGrupperingsid(
                    context = env.getContext(),
                    grupperingsid = env.getTypedArgument("grupperingsid"),
                    merkelapp = env.getTypedArgument("merkelapp"),
                    status = env.getTypedArgument("status"),
                )
            }
        }
    }

    data class NyStatusSakInput(
        val status: SaksStatusInput,
        val idempotencyKey: String?,
    )

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
    sealed interface NyStatusSakResultat

    @JsonTypeName("NyStatusSakVellykket")
    data class NyStatusSakVellykket(
        val id: UUID,
    ): NyStatusSakResultat


    private suspend fun nyStatusSak(
        context: ProdusentAPI.Context,
        id: UUID,
        status: NyStatusSakInput,
    ): NyStatusSakResultat {
        val sak = produsentRepository.hentSak(sakId = id)
            ?: return Error.SakFinnesIkke("sak med id=$id finnes ikke")
        return nyStatusSak(context = context, sak = sak, status = status)
    }

    private suspend fun nyStatusSakByGrupperingsid(
        context: ProdusentAPI.Context,
        grupperingsid: String,
        merkelapp: String,
        status: NyStatusSakInput,
    ): NyStatusSakResultat {
        val sak = produsentRepository.hentSak(merkelapp = merkelapp, grupperingsid = grupperingsid)
            ?: return Error.SakFinnesIkke("sak med merkelapp='$merkelapp' og grupperingsid='$grupperingsid' finnes ikke")
        return nyStatusSak(context = context, sak = sak, status = status)
    }

    private suspend fun nyStatusSak(
        context: ProdusentAPI.Context,
        sak: ProdusentModel.Sak,
        status: NyStatusSakInput,
    ) : NyStatusSakResultat {
        val produsent = hentProdusent(context) { error -> return error }

        tilgangsstyrMerkelapp(
            produsent,
            sak.merkelapp,
        ) { error -> return error }

        val hendelseId = UUID.randomUUID()

        val idempotencyKey =
            if (status.idempotencyKey != null)
                IdempotencyPrefix.USER_SUPPLIED.serialized + status.idempotencyKey
            else
                IdempotencyPrefix.GENERATED.serialized + hendelseId

        val existing = sak.statusoppdateringer.find {
            it.idempotencyKey == idempotencyKey
        }

        return when {
            existing == null -> {
                val nyStatusSakHendelse = Hendelse.NyStatusSak(
                    hendelseId = hendelseId,
                    virksomhetsnummer = sak.virksomhetsnummer,
                    produsentId = produsent.id,
                    kildeAppNavn = context.appName,
                    sakId = sak.id,
                    status = status.status.status.hendelseType,
                    overstyrStatustekstMed = status.status.overstyrStatustekstMed,
                    oppgittTidspunkt = status.status.tidspunkt,
                    mottattTidspunkt = OffsetDateTime.now(),
                    idempotensKey = idempotencyKey,
                )

                kafkaProducer.sendHendelse(nyStatusSakHendelse)
                produsentRepository.oppdaterModellEtterHendelse(nyStatusSakHendelse)
                NyStatusSakVellykket(
                    id = hendelseId
                )
            }
            status.status.isDuplicateOf(existing) -> {
                NyStatusSakVellykket(
                    id = existing.id
                )
            }
            else -> {
                Error.Konflikt(
                    feilmelding = "statusoppdatering med idempotency eksisterer, men er annerledes"
                )
            }
        }
    }
}

fun SaksStatusInput.isDuplicateOf(existing: ProdusentModel.SakStatusOppdatering): Boolean {
    return this.status.hendelseType == existing.status
            && this.overstyrStatustekstMed == existing.overstyrStatustekstMed
            && this.tidspunkt == existing.tidspunktOppgitt
}

