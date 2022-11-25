package no.nav.arbeidsgiver.notifikasjon.statistikk

import com.fasterxml.jackson.databind.node.NullNode
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.MultiGauge
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.BrukerKlikket
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarselFeilet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarselSendingsvindu.NKS_ÅPNINGSTID
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarselVellykket
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EpostVarselKontaktinfo
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.HendelseMetadata
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.NærmesteLederMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveUtført
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SmsVarselKontaktinfo
import no.nav.arbeidsgiver.notifikasjon.util.EksempelHendelse
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
            smsTekst = "sjalabais!",
            sendevindu = NKS_ÅPNINGSTID,
            sendeTidspunkt = null,
        )
        val bestilling = OppgaveOpprettet(
            merkelapp = "foo",
            eksternId = "42",
            mottakere = listOf(NærmesteLederMottaker(
                naermesteLederFnr = "314",
                ansattFnr = "33314",
                virksomhetsnummer = "1337"
            )),
            hendelseId = UUID.fromString("da89eafe-b31b-11eb-8529-0242ac000000"),
            notifikasjonId = UUID.fromString("da89eafe-b31b-11eb-8529-0242ac000000"),
            tekst = "teste",
            grupperingsid = "gr1",
            lenke = "foo.no/bar",
            opprettetTidspunkt = OffsetDateTime.now(UTC).truncatedTo(MILLIS),
            virksomhetsnummer = "1337",
            kildeAppNavn = "",
            produsentId = "",
            eksterneVarsler = listOf(epostBestilling, smsBestilling, smsBestilling2),
            hardDelete = null,
            frist = null,
            påminnelse = null,
        )
        val epostFeilet = EksterntVarselFeilet(
            virksomhetsnummer = bestilling.virksomhetsnummer,
            notifikasjonId = bestilling.notifikasjonId,
            hendelseId = UUID.fromString("da89eafe-b31b-11eb-8529-0242ac111111"),
            produsentId = bestilling.produsentId,
            kildeAppNavn = bestilling.kildeAppNavn,
            varselId = epostBestilling.varselId,
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

        val brukerKlikket = BrukerKlikket(
            virksomhetsnummer = bestilling.virksomhetsnummer,
            fnr = "1234567789",
            hendelseId = UUID.randomUUID(),
            notifikasjonId = bestilling.notifikasjonId,
            kildeAppNavn = "",
            produsentId = null,
        )

        val oppgaveUtført = OppgaveUtført(
            virksomhetsnummer = bestilling.virksomhetsnummer,
            hendelseId = UUID.randomUUID(),
            notifikasjonId = bestilling.notifikasjonId,
            kildeAppNavn = "",
            produsentId = "",
            hardDelete = null,
        )



        context("gitt hendelse med to varsler") {
            val meterRegistry = SimpleMeterRegistry()
            val gauge = MultiGauge.builder("antall_varsler")
                .description("Antall varsler")
                .register(meterRegistry)
            val antallUtførteGauge = MultiGauge.builder("antall_utforte")
                .description("Antall utførte (med histogram)")
                .register(meterRegistry)
            val antallUnikeVarselTekster = MultiGauge.builder("antall_unike_varseltekster")
                .description("Antall unike varseltekster")
                .register(meterRegistry)

            model.oppdaterModellEtterHendelse(bestilling, HendelseMetadata(now()))
            model.oppdaterModellEtterHendelse(epostFeilet, HendelseMetadata(now()))
            model.oppdaterModellEtterHendelse(smsVellykket, HendelseMetadata(now()))
            model.oppdaterModellEtterHendelse(brukerKlikket, HendelseMetadata(now()))
            model.oppdaterModellEtterHendelse(oppgaveUtført, HendelseMetadata(now()))

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

            it("utførthistogram inneholder informasjon om klikket på eller ikke") {
                antallUtførteGauge.register(model.antallUtførteHistogram(), true)
                val utførteOgKlikketPaa = meterRegistry.get("antall_utforte").tag("klikket_paa", "t").gauge().value()
                utførteOgKlikketPaa shouldBe 1
            }


            it("antall unike varsler registreres") {
                val rows = model.antallUnikeVarselTekster()
                antallUnikeVarselTekster.register(rows, true)

                val unikeEpostVarseltekster = meterRegistry.get("antall_unike_varseltekster").tag("varsel_type", "epost_kontaktinfo").gauge().value()
                val unikeSmsVarseltekster = meterRegistry.get("antall_unike_varseltekster").tag("varsel_type", "sms_kontaktinfo").gauge().value()
                unikeEpostVarseltekster shouldBe 1
                unikeSmsVarseltekster shouldBe 2
            }
        }

        context("SoftDelete") {
            val softdelete = HendelseModel.SoftDelete(
                virksomhetsnummer = "42",
                aggregateId = UUID.randomUUID(),
                hendelseId = UUID.randomUUID(),
                produsentId = "42",
                kildeAppNavn = "test:app",
                deletedAt = OffsetDateTime.now()
            )

            it("Feiler ikke dersom det ikke finnes noe tilhørende sak eller notifikasjon") {
                shouldNotThrowAny {
                    model.oppdaterModellEtterHendelse(softdelete, HendelseMetadata(now()))
                }
            }
        }

        context("OppgaveUtført nulstiller utgaatt_tidspunkt") {
            val oppgaveUtgått = HendelseModel.OppgaveUtgått(
                virksomhetsnummer = bestilling.virksomhetsnummer,
                notifikasjonId = bestilling.notifikasjonId,
                hendelseId = UUID.randomUUID(),
                produsentId = bestilling.produsentId,
                kildeAppNavn = bestilling.kildeAppNavn,
                hardDelete = null,
                utgaattTidspunkt = OffsetDateTime.now(),
            )

            model.oppdaterModellEtterHendelse(bestilling, HendelseMetadata(now()))
            model.oppdaterModellEtterHendelse(oppgaveUtgått, HendelseMetadata(now()))

            it("utgått oppgave er ikke med i histogram for utførte") {
               model.antallUtførteHistogram() shouldHaveSize 0
            }
            it("utgått oppgave er registrert i databasen") {
                model.antallUtgåtteOppgaver() shouldHaveSize 1
            }

            model.oppdaterModellEtterHendelse(oppgaveUtført, HendelseMetadata(now()))

            it("utført oppgave er nå med i histogram for utførte") {
                model.antallUtførteHistogram() shouldHaveSize 1
            }
            it("utgått oppgave er nullstilt i databasen") {
                model.antallUtgåtteOppgaver() shouldHaveSize 0
            }
        }
    }

    describe("Statistikk Idempotent oppførsel") {
        val metadata = HendelseMetadata(now())
        withData(EksempelHendelse.Alle) { hendelse ->
            model.oppdaterModellEtterHendelse(hendelse, metadata)
            model.oppdaterModellEtterHendelse(hendelse, metadata)
        }
    }
})
