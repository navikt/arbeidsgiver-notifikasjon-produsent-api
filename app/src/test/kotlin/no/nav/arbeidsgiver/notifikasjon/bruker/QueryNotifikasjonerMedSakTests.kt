package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.util.*
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

class QueryNotifikasjonerMedSakTests : DescribeSpec({
    val database = testDatabase(Bruker.databaseConfig)
    val queryModel = BrukerRepositoryImpl(database)

    val engine = ktorBrukerTestServer(
        brukerRepository = queryModel,
        altinn = AltinnStub { _, _ ->
            BrukerModel.Tilganger(
                listOf(
                    BrukerModel.Tilgang.Altinn(
                        virksomhet = "1",
                        servicecode = "1",
                        serviceedition = "1",
                    )
                )
            )
        }
    )

    context("Query.notifikasjoner med sak") {

        val opprettetTidspunkt = OffsetDateTime.parse("2017-12-03T10:15:30+01:00")
        val oppgaveUtenSakOpprettet = HendelseModel.OppgaveOpprettet(
            hendelseId = uuid("0"),
            notifikasjonId = uuid("0"),
            grupperingsid = "0",
            eksternId = "0",
            virksomhetsnummer = "1",
            produsentId = "1",
            kildeAppNavn = "1",
            eksterneVarsler = listOf(),
            opprettetTidspunkt = opprettetTidspunkt,
            merkelapp = "tag",
            tekst = "oppgave uten sak",
            mottakere = listOf(
                HendelseModel.AltinnMottaker(
                    virksomhetsnummer = "1",
                    serviceCode = "1",
                    serviceEdition = "1"
                )
            ),
            lenke = "#foo",
            hardDelete = null,
            frist = LocalDate.parse("2007-12-03"),
            påminnelse = null,
        ).also {
            queryModel.oppdaterModellEtterHendelse(it)
        }
        val beskjedUtenSakOpprettet = HendelseModel.BeskjedOpprettet(
            hendelseId = uuid("1"),
            notifikasjonId = uuid("1"),
            grupperingsid = "1",
            eksternId = "1",
            virksomhetsnummer = "1",
            produsentId = "1",
            kildeAppNavn = "1",
            eksterneVarsler = listOf(),
            opprettetTidspunkt = opprettetTidspunkt.minusHours(1),
            merkelapp = "tag",
            tekst = "beskjed uten sak",
            mottakere = listOf(
                HendelseModel.AltinnMottaker(
                    virksomhetsnummer = "1",
                    serviceCode = "1",
                    serviceEdition = "1"
                )
            ),
            lenke = "#foo",
            hardDelete = null,
        ).also {
            queryModel.oppdaterModellEtterHendelse(it)
        }
        
        val oppgaveMedSakOpprettet = HendelseModel.OppgaveOpprettet(
            hendelseId = uuid("2"),
            notifikasjonId = uuid("2"),
            grupperingsid = "2",
            eksternId = "2",
            virksomhetsnummer = "1",
            produsentId = "1",
            kildeAppNavn = "1",
            eksterneVarsler = listOf(),
            opprettetTidspunkt = opprettetTidspunkt.minusHours(2),
            merkelapp = "tag",
            tekst = "oppgave uten sak",
            mottakere = listOf(
                HendelseModel.AltinnMottaker(
                    virksomhetsnummer = "1",
                    serviceCode = "1",
                    serviceEdition = "1"
                )
            ),
            lenke = "#foo",
            hardDelete = null,
            frist = LocalDate.parse("2007-12-03"),
            påminnelse = null,
        ).also {
            queryModel.oppdaterModellEtterHendelse(it)
            val sakId = UUID.randomUUID()
            queryModel.oppdaterModellEtterHendelse(HendelseModel.SakOpprettet(
                hendelseId = sakId,
                sakId = sakId,
                virksomhetsnummer = it.virksomhetsnummer, 
                produsentId = it.produsentId, 
                kildeAppNavn = it.kildeAppNavn, 
                grupperingsid = it.grupperingsid!!,
                merkelapp = it.merkelapp,
                mottakere = it.mottakere,
                tittel = "Sakstittel for oppgave",
                lenke = it.lenke,
                oppgittTidspunkt = null, 
                mottattTidspunkt = OffsetDateTime.now(),
                hardDelete = null,
            ))
        }
        
        val beskjedMedSakOpprettet = HendelseModel.BeskjedOpprettet(
            hendelseId = uuid("3"),
            notifikasjonId = uuid("3"),
            grupperingsid = "3",
            eksternId = "3",
            virksomhetsnummer = "1",
            produsentId = "1",
            kildeAppNavn = "1",
            eksterneVarsler = listOf(),
            opprettetTidspunkt = opprettetTidspunkt.minusHours(3),
            merkelapp = "tag",
            tekst = "beskjed med sak",
            mottakere = listOf(
                HendelseModel.AltinnMottaker(
                    virksomhetsnummer = "1",
                    serviceCode = "1",
                    serviceEdition = "1"
                )
            ),
            lenke = "#foo",
            hardDelete = null,
        ).also {
            queryModel.oppdaterModellEtterHendelse(it)
            val sakId = UUID.randomUUID()
            queryModel.oppdaterModellEtterHendelse(HendelseModel.SakOpprettet(
                hendelseId = sakId,
                sakId = sakId,
                virksomhetsnummer = it.virksomhetsnummer,
                produsentId = it.produsentId,
                kildeAppNavn = it.kildeAppNavn,
                grupperingsid = it.grupperingsid!!,
                merkelapp = it.merkelapp,
                mottakere = it.mottakere,
                tittel = "Sakstittel for beskjed",
                lenke = it.lenke,
                oppgittTidspunkt = null,
                mottattTidspunkt = OffsetDateTime.now(),
                hardDelete = null,
            ))
        }
        
        val oppgaveMedSakFeilMottakerOpprettet = HendelseModel.OppgaveOpprettet(
            hendelseId = uuid("4"),
            notifikasjonId = uuid("4"),
            grupperingsid = "4",
            eksternId = "4",
            virksomhetsnummer = "1",
            produsentId = "1",
            kildeAppNavn = "1",
            eksterneVarsler = listOf(),
            opprettetTidspunkt = opprettetTidspunkt.minusHours(4),
            merkelapp = "tag",
            tekst = "oppgave med sak feil mottaker",
            mottakere = listOf(
                HendelseModel.AltinnMottaker(
                    virksomhetsnummer = "1",
                    serviceCode = "1",
                    serviceEdition = "1"
                )
            ),
            lenke = "#foo",
            hardDelete = null,
            frist = LocalDate.parse("2007-12-03"),
            påminnelse = null,
        ).also {
            queryModel.oppdaterModellEtterHendelse(it)
            val sakId = UUID.randomUUID()
            queryModel.oppdaterModellEtterHendelse(HendelseModel.SakOpprettet(
                hendelseId = sakId,
                sakId = sakId,
                virksomhetsnummer = it.virksomhetsnummer, 
                produsentId = it.produsentId, 
                kildeAppNavn = it.kildeAppNavn, 
                grupperingsid = it.grupperingsid!!,
                merkelapp = it.merkelapp,
                mottakere = listOf(
                    HendelseModel.AltinnMottaker(
                        virksomhetsnummer = "2",
                        serviceCode = "2",
                        serviceEdition = "2"
                    )
                ),
                tittel = "Sakstittel for oppgave feil mottaker",
                lenke = it.lenke,
                oppgittTidspunkt = null, 
                mottattTidspunkt = OffsetDateTime.now(),
                hardDelete = null,
            ))
        }
        
        val beskjedMedSakMedFeilMottakerOpprettet = HendelseModel.BeskjedOpprettet(
            hendelseId = uuid("5"),
            notifikasjonId = uuid("5"),
            grupperingsid = "5",
            eksternId = "5",
            virksomhetsnummer = "1",
            produsentId = "1",
            kildeAppNavn = "1",
            eksterneVarsler = listOf(),
            opprettetTidspunkt = opprettetTidspunkt.minusHours(5),
            merkelapp = "tag",
            tekst = "beskjed med sak feil mottaker",
            mottakere = listOf(
                HendelseModel.AltinnMottaker(
                    virksomhetsnummer = "1",
                    serviceCode = "1",
                    serviceEdition = "1"
                )
            ),
            lenke = "#foo",
            hardDelete = null,
        ).also {
            queryModel.oppdaterModellEtterHendelse(it)
            val sakId = UUID.randomUUID()
            queryModel.oppdaterModellEtterHendelse(HendelseModel.SakOpprettet(
                hendelseId = sakId,
                sakId = sakId,
                virksomhetsnummer = it.virksomhetsnummer,
                produsentId = it.produsentId,
                kildeAppNavn = it.kildeAppNavn,
                grupperingsid = it.grupperingsid!!,
                merkelapp = it.merkelapp,
                mottakere = listOf(
                    HendelseModel.AltinnMottaker(
                        virksomhetsnummer = "2",
                        serviceCode = "2",
                        serviceEdition = "2"
                    )
                ),
                tittel = "Sakstittel for beskjed feil mottaker",
                lenke = it.lenke,
                oppgittTidspunkt = null,
                mottattTidspunkt = OffsetDateTime.now(),
                hardDelete = null,
            ))
        }

        val response = engine.queryNotifikasjonerJson()

        it("response inneholder riktig data") {
            response.getTypedContent<List<BrukerAPI.Notifikasjon>>("notifikasjoner/notifikasjoner").let { notifikasjoner ->
                (notifikasjoner[0] as BrukerAPI.Notifikasjon.Oppgave).let {
                    it.id shouldBe oppgaveUtenSakOpprettet.aggregateId
                    it.sak should beNull()
                }
                (notifikasjoner[1] as BrukerAPI.Notifikasjon.Beskjed).let {
                    it.id shouldBe beskjedUtenSakOpprettet.aggregateId
                    it.sak should beNull()
                }
                (notifikasjoner[2] as BrukerAPI.Notifikasjon.Oppgave).let {
                    it.id shouldBe oppgaveMedSakOpprettet.aggregateId
                    it.sak shouldNot beNull()
                    it.sak!!.tittel shouldBe "Sakstittel for oppgave"
                }
                (notifikasjoner[3] as BrukerAPI.Notifikasjon.Beskjed).let {
                    it.id shouldBe beskjedMedSakOpprettet.aggregateId
                    it.sak shouldNot beNull()
                    it.sak!!.tittel shouldBe "Sakstittel for beskjed"
                }
                (notifikasjoner[4] as BrukerAPI.Notifikasjon.Oppgave).let {
                    it.id shouldBe oppgaveMedSakFeilMottakerOpprettet.aggregateId
                    it.sak should beNull()
                }
                (notifikasjoner[5] as BrukerAPI.Notifikasjon.Beskjed).let {
                    it.id shouldBe beskjedMedSakMedFeilMottakerOpprettet.aggregateId
                    it.sak should beNull()
                }
                notifikasjoner shouldHaveSize 6
            }
        }
    }
})

