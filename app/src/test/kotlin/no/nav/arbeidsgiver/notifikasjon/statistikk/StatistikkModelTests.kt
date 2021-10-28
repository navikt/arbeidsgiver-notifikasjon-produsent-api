package no.nav.arbeidsgiver.notifikasjon.statistikk

import com.fasterxml.jackson.databind.node.NullNode
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.MultiGauge
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.arbeidsgiver.notifikasjon.*
import no.nav.arbeidsgiver.notifikasjon.EksterntVarselSendingsvindu.NKS_ÅPNINGSTID
import no.nav.arbeidsgiver.notifikasjon.Hendelse.EksterntVarselFeilet
import no.nav.arbeidsgiver.notifikasjon.Hendelse.EksterntVarselVellykket
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import java.time.Instant.now
import java.time.OffsetDateTime
import java.time.ZoneOffset.UTC
import java.time.temporal.ChronoUnit.MILLIS
import java.util.*

class StatistikkModelTests : DescribeSpec({
    val database = testDatabase(Statistikk.databaseConfig)
    val model = StatistikkModel(database)

    describe("StatistikkModel") {
        val epostBestilling = EpostVarselKontaktinfo(
            varselId = UUID.fromString("da89eafe-b31b-11eb-8529-0242ac000001"),
            epostAddr = "foo@bar.baz",
            fnrEllerOrgnr = "1234567789",
            tittel = "tjobing!",
            htmlBody = "<body/>",
            sendevindu = NKS_ÅPNINGSTID,
            sendeTidspunkt = null,
        )
        val smsBestilling = SmsVarselKontaktinfo(
            varselId = UUID.fromString("da89eafe-b31b-11eb-8529-0242ac000002"),
            tlfnr = "foo@bar.baz",
            fnrEllerOrgnr = "1234567789",
            smsTekst = "tjobing!",
            sendevindu = NKS_ÅPNINGSTID,
            sendeTidspunkt = null,
        )
        val smsBestilling2 = SmsVarselKontaktinfo(
            varselId = UUID.fromString("da89eafe-b31b-11eb-8529-0242ac000003"),
            tlfnr = "foo@bar.baz",
            fnrEllerOrgnr = "1234567789",
            smsTekst = "tjobing!",
            sendevindu = NKS_ÅPNINGSTID,
            sendeTidspunkt = null,
        )
        val bestilling = Hendelse.BeskjedOpprettet(
            merkelapp = "foo",
            eksternId = "42",
            mottaker = NærmesteLederMottaker(
                naermesteLederFnr = "314",
                ansattFnr = "33314",
                virksomhetsnummer = "1337"
            ),
            hendelseId = UUID.fromString("da89eafe-b31b-11eb-8529-0242ac000000"),
            notifikasjonId = UUID.fromString("da89eafe-b31b-11eb-8529-0242ac000000"),
            tekst = "teste",
            grupperingsid = "gr1",
            lenke = "foo.no/bar",
            opprettetTidspunkt = OffsetDateTime.now(UTC).truncatedTo(MILLIS),
            virksomhetsnummer = "1337",
            kildeAppNavn = "",
            produsentId = "",
            eksterneVarsler = listOf(epostBestilling, smsBestilling, smsBestilling2)
        )
        val epostFeilet = EksterntVarselFeilet(
            virksomhetsnummer = bestilling.virksomhetsnummer,
            notifikasjonId = bestilling.notifikasjonId,
            hendelseId = UUID.fromString("da89eafe-b31b-11eb-8529-0242ac111111"),
            produsentId = bestilling.produsentId,
            kildeAppNavn = bestilling.kildeAppNavn,
            varselId =  epostBestilling.varselId,
            råRespons = NullNode.instance,
            altinnFeilkode = "42",
            feilmelding = "uwotm8"
        )
        val smsVellykket = EksterntVarselVellykket(
            virksomhetsnummer = bestilling.virksomhetsnummer,
            notifikasjonId = bestilling.notifikasjonId,
            hendelseId = UUID.fromString("da89eafe-b31b-11eb-8529-0242ac111112"),
            produsentId = bestilling.produsentId,
            kildeAppNavn = bestilling.kildeAppNavn,
            varselId = smsBestilling.varselId,
            råRespons = NullNode.instance,
        )

        context("gitt hendelse med to varsler") {
            val meterRegistry = SimpleMeterRegistry()
            val gauge = MultiGauge.builder("antall_varsler")
                .description("Antall varsler")
                .register(meterRegistry)
            model.oppdaterModellEtterHendelse(bestilling, HendelseMetadata(now()))
            model.oppdaterModellEtterHendelse(epostFeilet, HendelseMetadata(now()))
            model.oppdaterModellEtterHendelse(smsVellykket, HendelseMetadata(now()))

            it("opprettes statistikk i databasen") {
                val antallVarsler = model.antallVarsler()
                gauge.register(antallVarsler, true)

                val bestilt = meterRegistry.get("antall_varsler").tag("status", "bestilt").gauge().value()
                val feilet = meterRegistry.get("antall_varsler").tag("status", "feilet").gauge().value()
                val vellykket = meterRegistry.get("antall_varsler").tag("status", "vellykket").gauge().value()
                bestilt shouldBe 1
                feilet shouldBe 1
                vellykket shouldBe 1
            }
        }
    }
})
