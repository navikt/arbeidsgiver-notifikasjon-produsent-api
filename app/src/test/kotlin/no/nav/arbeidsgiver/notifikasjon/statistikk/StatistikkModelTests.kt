package no.nav.arbeidsgiver.notifikasjon.statistikk

import com.fasterxml.jackson.databind.node.NullNode
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
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
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import java.time.Instant.now
import java.time.OffsetDateTime
import java.time.ZoneOffset.UTC
import java.time.temporal.ChronoUnit.MILLIS
import java.util.*

class StatistikkModelTests : DescribeSpec({


    fun opprettSak(
        id: String,
    ): HendelseModel.SakOpprettet {
        val uuid = uuid(id)
        return HendelseModel.SakOpprettet(
            virksomhetsnummer = "1",
            produsentId = "1",
            kildeAppNavn = "1",
            hendelseId = uuid,
            sakId = uuid,
            grupperingsid = "1",
            merkelapp = "tag",
            mottakere = listOf(
                HendelseModel.AltinnMottaker(
                    virksomhetsnummer = "1",
                    serviceCode = "1",
                    serviceEdition = "1"
                )
            ),
            tittel = "tjohei",
            lenke = "#foo",
            oppgittTidspunkt = OffsetDateTime.parse("2017-12-03T10:15:30+01:00"),
            mottattTidspunkt = OffsetDateTime.parse("2017-12-03T10:15:30+01:00"),
            hardDelete = null,
        )
    }

    fun opprettOppgave(
        id: String,
        merkelapp: String,
        grupperingsid: String?,
    ) = OppgaveOpprettet(
        virksomhetsnummer = "1",
        produsentId = "1",
        hendelseId = uuid(id),
        notifikasjonId = uuid(id),
        sakId = null,
        kildeAppNavn = "1",
        grupperingsid = grupperingsid,
        eksternId = "1",
        eksterneVarsler = listOf(),
        opprettetTidspunkt = OffsetDateTime.parse("2017-12-03T10:15:30+01:00"),
        merkelapp = merkelapp,
        tekst = "tjohei",
        mottakere = listOf(
            HendelseModel.AltinnMottaker(
                virksomhetsnummer = "1",
                serviceCode = "1",
                serviceEdition = "1"
            )
        ),
        lenke = "#foo",
        hardDelete = null,
        frist = null,
        påminnelse = null,
    )

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
        val altinntjenesteBestilling = HendelseModel.AltinntjenesteVarselKontaktinfo(
            varselId = UUID.fromString("da89eafe-b31b-11eb-8529-0242ac000004"),
            virksomhetsnummer = "1234567789",
            serviceCode = "4631",
            serviceEdition = "1",
            tittel = "jau",
            innhold = "jaujau",
            sendevindu = NKS_ÅPNINGSTID,
            sendeTidspunkt = null,
        )
        val bestilling = OppgaveOpprettet(
            merkelapp = "foo",
            eksternId = "42",
            mottakere = listOf(
                NærmesteLederMottaker(
                    naermesteLederFnr = "314",
                    ansattFnr = "33314",
                    virksomhetsnummer = "1337"
                )
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
            eksterneVarsler = listOf(epostBestilling, smsBestilling, smsBestilling2, altinntjenesteBestilling),
            hardDelete = null,
            frist = null,
            påminnelse = null,
            sakId = null,
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
        val altinntjenesteVellykket = EksterntVarselVellykket(
            virksomhetsnummer = bestilling.virksomhetsnummer,
            notifikasjonId = bestilling.notifikasjonId,
            hendelseId = UUID.fromString("da89eafe-b31b-11eb-8529-0242ac111113"),
            produsentId = bestilling.produsentId,
            kildeAppNavn = bestilling.kildeAppNavn,
            varselId = altinntjenesteBestilling.varselId,
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
            nyLenke = null,
            utfoertTidspunkt = OffsetDateTime.parse("2023-01-05T00:00:00+01")
        )



        context("gitt hendelse med to varsler") {
            val database = testDatabase(Statistikk.databaseConfig)
            val model = StatistikkModel(database)
            val meterRegistry = SimpleMeterRegistry()
            val gauge = MultiGauge.builder("antall_varsler")
                .description("Antall varsler")
                .register(meterRegistry)

            model.oppdaterModellEtterHendelse(bestilling, HendelseMetadata(now()))
            model.oppdaterModellEtterHendelse(epostFeilet, HendelseMetadata(now()))
            model.oppdaterModellEtterHendelse(smsVellykket, HendelseMetadata(now()))
            model.oppdaterModellEtterHendelse(altinntjenesteVellykket, HendelseMetadata(now()))
            model.oppdaterModellEtterHendelse(brukerKlikket, HendelseMetadata(now()))
            model.oppdaterModellEtterHendelse(oppgaveUtført, HendelseMetadata(now()))

            it("opprettes statistikk i databasen") {
                val antallVarsler = model.antallVarsler()
                gauge.register(antallVarsler, true)

                val bestilt = meterRegistry.get("antall_varsler").tag("status", "bestilt").gauge().value()
                val feilet = meterRegistry.get("antall_varsler").tag("status", "feilet").gauge().value()
                val vellykketSms =
                    meterRegistry.get("antall_varsler").tag("status", "vellykket").tag("varsel_type", "sms_kontaktinfo")
                        .gauge().value()
                val vellykketAltinn = meterRegistry.get("antall_varsler").tag("status", "vellykket")
                    .tag("varsel_type", "altinntjeneste_kontaktinfo").gauge().value()
                bestilt shouldBe 1
                feilet shouldBe 1
                vellykketSms shouldBe 1
                vellykketAltinn shouldBe 1
            }
        }

        context("SoftDelete") {
            val database = testDatabase(Statistikk.databaseConfig)
            val model = StatistikkModel(database)
            val softdelete = HendelseModel.SoftDelete(
                virksomhetsnummer = "42",
                aggregateId = UUID.randomUUID(),
                hendelseId = UUID.randomUUID(),
                produsentId = "42",
                kildeAppNavn = "test:app",
                deletedAt = OffsetDateTime.now(),
                grupperingsid = null,
                merkelapp = null,
            )

            it("Feiler ikke dersom det ikke finnes noe tilhørende sak eller notifikasjon") {
                shouldNotThrowAny {
                    model.oppdaterModellEtterHendelse(softdelete, HendelseMetadata(now()))
                }
            }
        }
    }

    describe("Statistikk Idempotent oppførsel") {
        val database = testDatabase(Statistikk.databaseConfig)
        val model = StatistikkModel(database)
        val metadata = HendelseMetadata(now())
        withData(EksempelHendelse.Alle) { hendelse ->
            model.oppdaterModellEtterHendelse(hendelse, metadata)
            model.oppdaterModellEtterHendelse(hendelse, metadata)
        }
    }

    describe("SoftDelete på sak sletter også relaterte notifikasjoner") {
        val database = testDatabase(Statistikk.databaseConfig)
        val model = StatistikkModel(database)
        model.oppdaterModellEtterHendelse(opprettSak("1"), HendelseMetadata(now()))
        model.oppdaterModellEtterHendelse(opprettOppgave("01", "tag", "1"), HendelseMetadata(now()))
        model.oppdaterModellEtterHendelse(opprettOppgave("02", "tag", "2"), HendelseMetadata(now()))
        model.oppdaterModellEtterHendelse(opprettOppgave("03", "tag2", "1"), HendelseMetadata(now()))
        model.oppdaterModellEtterHendelse(
            HendelseModel.SoftDelete(
                virksomhetsnummer = "1",
                aggregateId = uuid("1"),
                hendelseId = uuid("1"),
                produsentId = "1",
                kildeAppNavn = "1",
                deletedAt = OffsetDateTime.now(),
                grupperingsid = "1",
                merkelapp = "tag",
            ), HendelseMetadata(now())
        )
        it("Notifikasjoner som kun har samme grupperingsid og merkelapp blir merket som SoftDeleted") {
            val notifikasjoner = database.nonTransactionalExecuteQuery(
                """
            SELECT notifikasjon_id FROM notifikasjon where soft_deleted_tidspunkt is null;
            """.trimIndent(),
                transform = { getObject("notifikasjon_id", UUID::class.java) }
            )
            notifikasjoner shouldContainExactlyInAnyOrder listOf(uuid("2"), uuid("3"))
        }
    }

    describe("HardDelete av sak sletter også relaterte notifikasjoner") {
        val database = testDatabase(Statistikk.databaseConfig)
        val model = StatistikkModel(database)
        model.oppdaterModellEtterHendelse(opprettSak("1"), HendelseMetadata(now()))
        model.oppdaterModellEtterHendelse(opprettOppgave("01", "tag", "1"), HendelseMetadata(now()))
        model.oppdaterModellEtterHendelse(opprettOppgave("02", "tag", "2"), HendelseMetadata(now()))
        model.oppdaterModellEtterHendelse(opprettOppgave("03", "tag2", "1"), HendelseMetadata(now()))
        model.oppdaterModellEtterHendelse(
            HendelseModel.HardDelete(
                virksomhetsnummer = "1",
                aggregateId = uuid("1"),
                hendelseId = uuid("1"),
                produsentId = "1",
                kildeAppNavn = "1",
                deletedAt = OffsetDateTime.now(),
                grupperingsid = "1",
                merkelapp = "tag",
            ), HendelseMetadata(now())
        )
        it("Notifikasjoner som kun har samme grupperingsid og merkelapp blir merket som SoftDeleted") {
            val notifikasjoner = database.nonTransactionalExecuteQuery(
                """
            SELECT notifikasjon_id FROM notifikasjon where hard_deleted_tidspunkt is null;
            """.trimIndent(),
                transform = { getObject("notifikasjon_id", UUID::class.java) }
            )
            notifikasjoner shouldContainExactlyInAnyOrder listOf(uuid("2"), uuid("3"))
        }
    }
})
