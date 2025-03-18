package no.nav.arbeidsgiver.notifikasjon.skedulert_påminnelse

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.KalenderavtaleTilstand.AVLYST
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.KalenderavtaleTilstand.VENTER_SVAR_FRA_ARBEIDSGIVER
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.ISO8601Period
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import java.time.OffsetDateTime


class KalenderavtaleTests : DescribeSpec({
    describe("kalenderavtale med påminnelse sendes") {
        val (service, hendelseProdusent) = setupEngine()
        service.processHendelse(kalenderavtaleOpprettet)

        it("Sender påminnelse") {
            service.sendAktuellePåminnelser(now = kalenderavtaleOpprettet.påminnelse!!.tidspunkt.påminnelseTidspunkt)
            hendelseProdusent.hendelser shouldHaveSize 1
        }
    }

    describe("kalenderavtale med påminnelse markeres som avlyst") {
        val (service, hendelseProdusent) = setupEngine()
        service.processHendelse(kalenderavtaleOpprettet)
        service.processHendelse(
            kalenderavtaleOppdatert.copy(
                tilstand = AVLYST,
            )
        )

        it("Sender ikke påminnelse") {
            service.sendAktuellePåminnelser(now = kalenderavtaleOpprettet.påminnelse!!.tidspunkt.påminnelseTidspunkt)
            hendelseProdusent.hendelser shouldHaveSize 0
        }
    }

    describe("kalenderavtale med påminnelse hvor starttidspunkt endres") {
        val (service, hendelseProdusent) = setupEngine()
        service.processHendelse(kalenderavtaleOpprettet)
        service.processHendelse(
            kalenderavtaleOppdatert.copy(
                startTidspunkt = startTidspunkt.minusMinutes(1),
            )
        )

        it("Sender ikke påminnelse") {
            service.sendAktuellePåminnelser(now = kalenderavtaleOpprettet.påminnelse!!.tidspunkt.påminnelseTidspunkt)
            hendelseProdusent.hendelser shouldHaveSize 0
        }
    }

    describe("kalenderavtale med påminnelse hvor påminnelse endres") {
        val (service, hendelseProdusent) = setupEngine()
        service.processHendelse(kalenderavtaleOpprettet)
        val nyttTidspunkt = HendelseModel.PåminnelseTidspunkt.createAndValidateFørStartTidspunkt(
            førStartTidpunkt = ISO8601Period.parse("P2D"),
            notifikasjonOpprettetTidspunkt = opprettetTidspunkt,
            startTidspunkt = startTidspunkt,
        )
        service.processHendelse(
            kalenderavtaleOppdatert.copy(
                påminnelse = kalenderavtaleOpprettet.påminnelse!!.copy(
                    tidspunkt = nyttTidspunkt
                ),
            )
        )

        it("Sender kun nyeste påminnelse") {
            service.sendAktuellePåminnelser(now = kalenderavtaleOpprettet.påminnelse.tidspunkt.påminnelseTidspunkt)
            hendelseProdusent.hendelser shouldHaveSize 1
            hendelseProdusent.hendelser.first().let {
                it should beOfType<HendelseModel.PåminnelseOpprettet>()
                it as HendelseModel.PåminnelseOpprettet
                it.tidspunkt shouldBe nyttTidspunkt
            }
        }
    }

    describe("kalenderavtale med påminnelse som blir hard deleted") {
        val (service, hendelseProdusent) = setupEngine()
        service.processHendelse(kalenderavtaleOpprettet)
        service.processHendelse(HendelseModel.HardDelete(
            aggregateId = kalenderavtaleOpprettet.aggregateId,
            virksomhetsnummer = kalenderavtaleOpprettet.virksomhetsnummer,
            hendelseId = uuid("2"),
            produsentId = kalenderavtaleOpprettet.virksomhetsnummer,
            kildeAppNavn = kalenderavtaleOpprettet.virksomhetsnummer,
            deletedAt = OffsetDateTime.now(),
            grupperingsid = null,
            merkelapp = null,
        ))

        it("Sender ikke påminnelse") {
            service.sendAktuellePåminnelser(now = kalenderavtaleOpprettet.påminnelse!!.tidspunkt.påminnelseTidspunkt)
            hendelseProdusent.hendelser shouldHaveSize 0
        }
    }

    describe("kalenderavtale med påminnelse som blir soft deleted") {
        val (service, hendelseProdusent) = setupEngine()
        service.processHendelse(kalenderavtaleOpprettet)
        service.processHendelse(HendelseModel.SoftDelete(
            aggregateId = kalenderavtaleOpprettet.aggregateId,
            virksomhetsnummer = kalenderavtaleOpprettet.virksomhetsnummer,
            hendelseId = uuid("2"),
            produsentId = kalenderavtaleOpprettet.virksomhetsnummer,
            kildeAppNavn = kalenderavtaleOpprettet.virksomhetsnummer,
            deletedAt = OffsetDateTime.now(),
            grupperingsid = null,
            merkelapp = null,
        ))

        it("Sender ikke påminnelse") {
            service.sendAktuellePåminnelser(now = kalenderavtaleOpprettet.påminnelse!!.tidspunkt.påminnelseTidspunkt)
            hendelseProdusent.hendelser shouldHaveSize 0
        }
    }

})

private val opprettetTidspunkt = OffsetDateTime.parse("2021-01-01T12:00:00Z")
private val startTidspunkt = opprettetTidspunkt.plusDays(7).toLocalDateTime()
private val kalenderavtaleOpprettet = HendelseModel.KalenderavtaleOpprettet(
    virksomhetsnummer = "1",
    notifikasjonId = uuid("1"),
    hendelseId = uuid("1"),
    produsentId = "eksempel-produsent-id",
    kildeAppNavn = "eksempel-kilde-app-navn",
    merkelapp = "eksempel-merkelapp",
    grupperingsid = "eksempel-grupperingsid-42",
    eksternId = "1",
    mottakere = listOf(
        HendelseModel.AltinnMottaker(
            virksomhetsnummer = "1".repeat(9),
            serviceCode = "1",
            serviceEdition = "1"
        )
    ),
    hardDelete = null,
    påminnelse = HendelseModel.Påminnelse(
        tidspunkt = HendelseModel.PåminnelseTidspunkt.createAndValidateFørStartTidspunkt(
            førStartTidpunkt = ISO8601Period.parse("P1D"),
            notifikasjonOpprettetTidspunkt = opprettetTidspunkt,
            startTidspunkt = startTidspunkt,
        ),
        eksterneVarsler = listOf(
            HendelseModel.SmsVarselKontaktinfo(
                varselId = uuid("3"),
                fnrEllerOrgnr = "1",
                tlfnr = "1",
                smsTekst = "hey",
                sendevindu = HendelseModel.EksterntVarselSendingsvindu.LØPENDE,
                sendeTidspunkt = null
            ),
            HendelseModel.EpostVarselKontaktinfo(
                varselId = uuid("4"),
                fnrEllerOrgnr = "1",
                epostAddr = "1",
                tittel = "hey",
                htmlBody = "body",
                sendevindu = HendelseModel.EksterntVarselSendingsvindu.LØPENDE,
                sendeTidspunkt = null
            ),
        )
    ),
    sakId = uuid("42"),
    lenke = "https://foo.no",
    tekst = "foo",
    opprettetTidspunkt = opprettetTidspunkt,
    tilstand = VENTER_SVAR_FRA_ARBEIDSGIVER,
    startTidspunkt = startTidspunkt,
    sluttTidspunkt = null,
    lokasjon = null,
    erDigitalt = false,
    eksterneVarsler = listOf(),
)
private val kalenderavtaleOppdatert = HendelseModel.KalenderavtaleOppdatert(
    virksomhetsnummer = kalenderavtaleOpprettet.virksomhetsnummer,
    notifikasjonId = kalenderavtaleOpprettet.notifikasjonId,
    hendelseId = uuid("2"),
    produsentId = kalenderavtaleOpprettet.produsentId,
    kildeAppNavn = kalenderavtaleOpprettet.kildeAppNavn,
    merkelapp = kalenderavtaleOpprettet.merkelapp,
    grupperingsid = kalenderavtaleOpprettet.grupperingsid,
    hardDelete = null,
    påminnelse = null,
    lenke = "https://foo.no",
    tekst = "foo",
    opprettetTidspunkt = opprettetTidspunkt.toInstant(),
    oppdatertTidspunkt = opprettetTidspunkt.toInstant(),
    tilstand = null,
    startTidspunkt = null,
    sluttTidspunkt = null,
    lokasjon = null,
    erDigitalt = false,
    idempotenceKey = "eksempel-idempotence-key",
    eksterneVarsler = listOf(),
)