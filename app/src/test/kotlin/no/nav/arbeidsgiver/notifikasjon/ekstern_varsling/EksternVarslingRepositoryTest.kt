package no.nav.arbeidsgiver.notifikasjon.ekstern_varsling

import com.fasterxml.jackson.databind.node.NullNode
import jakarta.xml.bind.JAXBElement
import kotlinx.coroutines.delay
import no.altinn.services.common.fault._2009._10.AltinnFault
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinnressursVarselKontaktinfo
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.AltinntjenesteVarselKontaktinfo
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EksterntVarselSendingsvindu
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.EpostVarselKontaktinfo
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.SmsVarselKontaktinfo
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.getUuid
import no.nav.arbeidsgiver.notifikasjon.util.asMap
import no.nav.arbeidsgiver.notifikasjon.util.uuid
import no.nav.arbeidsgiver.notifikasjon.util.withTestDatabase
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*
import javax.xml.namespace.QName
import kotlin.test.*

class EksternVarslingRepositoryTest {

    val oppgaveOpprettet = OppgaveOpprettet(
        virksomhetsnummer = "1",
        notifikasjonId = uuid("1"),
        hendelseId = uuid("2"),
        produsentId = "1",
        kildeAppNavn = "1",
        merkelapp = "tag",
        grupperingsid = "42",
        eksternId = "1",
        mottakere = listOf(
            AltinnMottaker(
                virksomhetsnummer = "1",
                serviceCode = "1",
                serviceEdition = "1"
            )
        ),
        tekst = "1",
        lenke = "",
        opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01+00"),
        eksterneVarsler = listOf(
            SmsVarselKontaktinfo(
                varselId = uuid("3"),
                fnrEllerOrgnr = "1",
                tlfnr = "1",
                smsTekst = "hey",
                sendevindu = EksterntVarselSendingsvindu.LØPENDE,
                sendeTidspunkt = null
            ),
            EpostVarselKontaktinfo(
                varselId = uuid("4"),
                fnrEllerOrgnr = "1",
                epostAddr = "1",
                tittel = "hey",
                htmlBody = "body",
                sendevindu = EksterntVarselSendingsvindu.LØPENDE,
                sendeTidspunkt = null
            ),
            AltinntjenesteVarselKontaktinfo(
                varselId = uuid("5"),
                virksomhetsnummer = "1",
                serviceCode = "1",
                serviceEdition = "1",
                tittel = "hey",
                innhold = "body",
                sendevindu = EksterntVarselSendingsvindu.LØPENDE,
                sendeTidspunkt = null
            ),
            AltinnressursVarselKontaktinfo(
                varselId = uuid("5"),
                virksomhetsnummer = "1",
                ressursId = "foo",
                epostTittel = "hey",
                epostHtmlBody = "body",
                smsTekst = "sms",
                sendevindu = EksterntVarselSendingsvindu.LØPENDE,
                sendeTidspunkt = null
            )
        ),
        hardDelete = null,
        frist = null,
        påminnelse = null,
        sakId = null,
    )

    @Test
    fun `Getting and deleting jobs`() = withTestDatabase(EksternVarsling.databaseConfig) { database ->
        val repository = EksternVarslingRepository(database)

        repository.oppdaterModellEtterHendelse(oppgaveOpprettet)

        val id1 = repository.findJob(lockTimeout = Duration.ofMinutes(1))
        // should pick and delete a job
        assertNotNull(id1)
        assertTrue(listOf(uuid("3"), uuid("4"), uuid("5")).contains(id1))
        assertNotNull(repository.findVarsel(id1))

        repository.deleteFromJobQueue(id1)

        val id2 = repository.findJob(lockTimeout = Duration.ofMinutes(1))
        // should pick and delete another job
        assertNotNull(id2)
        assertNotEquals(id1, id2)
        assertTrue(listOf(uuid("3"), uuid("4"), uuid("5")).contains(id2))
        assertNotNull(repository.findVarsel(id2))

        repository.deleteFromJobQueue(id2)

        val id3 = repository.findJob(lockTimeout = Duration.ofMinutes(1))
        // should pick and delete last job
        assertNotNull(id3)
        assertNotEquals(id3, id1)
        assertNotEquals(id3, id2)
        assertTrue(listOf(uuid("3"), uuid("4"), uuid("5")).contains(id3))
        assertNotNull(repository.findVarsel(id3))

        repository.deleteFromJobQueue(id3)

        val id4 = repository.findJob(lockTimeout = Duration.ofMinutes(1))
        // should be no more jobs to pick
        assertNull(id4)
    }

    @Test
    fun `Can't pick a job while locked`() = withTestDatabase(EksternVarsling.databaseConfig) { database ->
        val repository = EksternVarslingRepository(database)

        repository.oppdaterModellEtterHendelse(
            oppgaveOpprettet.copy(
                eksterneVarsler = oppgaveOpprettet.eksterneVarsler.subList(0, 1)
            )
        )

        val id1 = repository.findJob(lockTimeout = Duration.ofDays(1))
        val id2 = repository.findJob(lockTimeout = Duration.ofDays(1))

        // should pick up the single job we have
        assertEquals(uuid("3"), id1)

        // should not pick up the single, locked job
        assertNull(id2)
    }

    @Test
    fun `auto-release locks`() = withTestDatabase(EksternVarsling.databaseConfig) { database ->
        val repository = EksternVarslingRepository(database)

        // release time-out
        repository.oppdaterModellEtterHendelse(
            oppgaveOpprettet.copy(
                eksterneVarsler = oppgaveOpprettet.eksterneVarsler.subList(0, 1)
            )
        )

        val id1 = repository.findJob(lockTimeout = Duration.ofMillis(1))
        delay(Duration.ofMillis(100).toMillis())
        val abandonedLocks = repository.releaseTimedOutJobLocks()

        assertEquals(1, abandonedLocks.size)
        assertEquals(id1, abandonedLocks[0].varselId)

        val id2 = repository.findJob(lockTimeout = Duration.ofMinutes(1))
        assertEquals(id2, id1)
    }


    @Test
    fun `release and reacquire lock`() = withTestDatabase(EksternVarsling.databaseConfig) { database ->
        val repository = EksternVarslingRepository(database)

        repository.oppdaterModellEtterHendelse(
            oppgaveOpprettet.copy(
                eksterneVarsler = oppgaveOpprettet.eksterneVarsler.subList(0, 1)
            )
        )

        val id1 = repository.findJob(lockTimeout = Duration.ofMinutes(1))!!

        // should get the job
        assertEquals(uuid("3"), id1)

        // should be returned to work queue
        repository.returnToJobQueue(id1)

        val id2 = repository.findJob(lockTimeout = Duration.ofMinutes(1))!!
        // should get it again
        assertEquals(uuid("3"), id2)
    }

    @Test
    fun `read and write of notification`() = withTestDatabase(EksternVarsling.databaseConfig) { database ->
        val repository = EksternVarslingRepository(database)

        repository.oppdaterModellEtterHendelse(oppgaveOpprettet)

        val id1 = repository.findJob(lockTimeout = Duration.ofMinutes(1))
        val id2 = repository.findJob(lockTimeout = Duration.ofMinutes(1))

        // har fått to forskjellige id-er
        assertNotNull(id1)
        assertNotNull(id2)
        assertNotEquals(id1, id2)

        val varsel1 = repository.findVarsel(id1)
        val varsel2 = repository.findVarsel(id2)


        // har fått to varsler av forskjellig type
        assertNotNull(varsel1)

        assertNotNull(varsel2)

        val type1 = varsel1.data.eksternVarsel::class
        val type2 = varsel2.data.eksternVarsel::class

        assertNotEquals(type1, type2)

        assertTrue(listOf(EksternVarsel.Sms::class, EksternVarsel.Epost::class).contains(type1))
        assertTrue(listOf(EksternVarsel.Sms::class, EksternVarsel.Epost::class).contains(type2))

    }

    @Test
    fun `Kan gå gjennom tilstandene ok`() = withTestDatabase(EksternVarsling.databaseConfig) { database ->
        val repository = EksternVarslingRepository(database)

        repository.oppdaterModellEtterHendelse(
            oppgaveOpprettet.copy(
                eksterneVarsler = oppgaveOpprettet.eksterneVarsler.subList(0, 1)
            )
        )

        val id1 = repository.findJob(lockTimeout = Duration.ofDays(10))!!
        val varsel1 = repository.findVarsel(id1)!!

        // should be Ny
        varsel1 as EksternVarselTilstand.Ny

        repository.markerSomSendtAndReleaseJob(
            id1,
            AltinnVarselKlientResponse.Ok(
                rå = NullNode.instance
            )
        )

        val id2 = repository.findJob(lockTimeout = Duration.ofDays(1))!!
        val varsel2 = repository.findVarsel(id2)

        // should be utført
        varsel2 as EksternVarselTilstand.Sendt

        repository.markerSomKvittertAndDeleteJob(id2)

        val id3 = repository.findJob(lockTimeout = Duration.ofDays(1))

        // no more work
        assertNull(id3)

        val varsel3 = repository.findVarsel(id2)!!
        // should be completed
        varsel3 as EksternVarselTilstand.Kvittert
        varsel3.response as AltinnResponse.Ok
    }

    @Test
    fun `Kan gå gjennom tilstandene altinn-feil`() = withTestDatabase(EksternVarsling.databaseConfig) { database ->
        val repository = EksternVarslingRepository(database)

        repository.oppdaterModellEtterHendelse(
            oppgaveOpprettet.copy(
                eksterneVarsler = oppgaveOpprettet.eksterneVarsler.subList(0, 1)
            )
        )

        val id1 = repository.findJob(lockTimeout = Duration.ofDays(10))!!
        val varsel1 = repository.findVarsel(id1)!!

        // should be Ny
        varsel1 as EksternVarselTilstand.Ny

        repository.markerSomSendtAndReleaseJob(
            id1,
            AltinnVarselKlientResponse.Feil(
                rå = NullNode.instance,
                altinnFault = AltinnFault().apply {
                    errorID = 1
                    altinnErrorMessage = JAXBElement(QName(""), String::class.java, "hallo")
                }
            )
        )

        val id2 = repository.findJob(lockTimeout = Duration.ofDays(1))!!
        val varsel2 = repository.findVarsel(id2)

        // should be utført
        varsel2 as EksternVarselTilstand.Sendt

        repository.markerSomKvittertAndDeleteJob(id2)

        val id3 = repository.findJob(lockTimeout = Duration.ofDays(1))

        // no more work
        assertNull(id3)

        val varsel3 = repository.findVarsel(id2)!!
        // should be failed
        varsel3 as EksternVarselTilstand.Kvittert
        val response = varsel3.response
        response as AltinnResponse.Feil
        assertEquals("1", response.feilkode)
        assertEquals("hallo", response.feilmelding)
    }

    @Test
    fun `Kan gå gjennom tilstandene altinn-feil altinn 3`() = withTestDatabase(EksternVarsling.databaseConfig) { database ->
        val repository = EksternVarslingRepository(database)

        repository.oppdaterModellEtterHendelse(
            oppgaveOpprettet.copy(
                eksterneVarsler = oppgaveOpprettet.eksterneVarsler.takeLast(1)
            )
        )

        val id1 = repository.findJob(lockTimeout = Duration.ofDays(10))!!
        val varsel1 = repository.findVarsel(id1)!!

        // should be Ny
        varsel1 as EksternVarselTilstand.Ny

        repository.markerSomSendtAndReleaseJob(
            id1,
            Altinn3VarselKlient.ErrorResponse(
                rå = NullNode.instance,
                message = "woopsies",
                code = "400"
            )
        )

        val id2 = repository.findJob(lockTimeout = Duration.ofDays(1))!!
        val varsel2 = repository.findVarsel(id2)

        // should be utført
        varsel2 as EksternVarselTilstand.Sendt

        repository.markerSomKvittertAndDeleteJob(id2)

        val id3 = repository.findJob(lockTimeout = Duration.ofDays(1))

        // no more work
        assertNull(id3)

        val varsel3 = repository.findVarsel(id2)!!
        // should be failed
        varsel3 as EksternVarselTilstand.Kvittert
        val response = varsel3.response
        response as AltinnResponse.Feil
        assertEquals("400", response.feilkode)
        assertEquals("woopsies", response.feilmelding)
    }

    @Test
    fun `Hard delete event for sak`() = withTestDatabase(EksternVarsling.databaseConfig) { database ->
        val repository = EksternVarslingRepository(database)

        repository.oppdaterModellEtterHendelse(oppgaveOpprettet)
        val sakId = uuid("442")
        val hardDelete =
            HendelseModel.HardDelete(
                virksomhetsnummer = "42",
                aggregateId = sakId,
                hendelseId = sakId,
                produsentId = "42",
                kildeAppNavn = "test:app",
                deletedAt = OffsetDateTime.now(),
                grupperingsid = oppgaveOpprettet.grupperingsid,
                merkelapp = oppgaveOpprettet.merkelapp,
            )

        // oppdater modell etter hendelse feiler ikke
        repository.oppdaterModellEtterHendelse(hardDelete) // does not throw

        // registrerer hard delete for oppgaven tilknyttet saken hard delete gjelder for
        assertEquals(
            listOf(oppgaveOpprettet.notifikasjonId),
            database.nonTransactionalExecuteQuery(
                """
                select * from hard_delete where notifikasjon_id = ?
                """,
                setup = {
                    uuid(oppgaveOpprettet.notifikasjonId)
                },
                transform = {
                    getUuid("notifikasjon_id")
                }
            )
        )

        // fjerner info fra lookup tabell
        with(database.nonTransactionalExecuteQuery(
            "select * from merkelapp_grupperingsid_notifikasjon where notifikasjon_id = ?",
            { uuid(oppgaveOpprettet.notifikasjonId) }
        ) { asMap() }) {
            assertTrue(isEmpty())
        }
    }

    @Test
    fun `Oppgave med påminnelse fører ikke til varsel nå`() = withTestDatabase(EksternVarsling.databaseConfig) { database ->
        val repository = EksternVarslingRepository(database)

        val varselId = UUID.randomUUID()
        OppgaveOpprettet(
            virksomhetsnummer = "1",
            notifikasjonId = UUID.randomUUID(),
            hendelseId = UUID.randomUUID(),
            produsentId = "fager",
            kildeAppNavn = "local:local:local",
            merkelapp = "merkelapp",
            eksternId = "ekstern-id",
            mottakere = listOf(
                AltinnMottaker(
                    serviceCode = "",
                    serviceEdition = "",
                    virksomhetsnummer = "",
                )
            ),
            tekst = "tekst",
            grupperingsid = null,
            lenke = "#",
            opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01+01"),
            eksterneVarsler = listOf(),
            hardDelete = null,
            frist = null,
            påminnelse = HendelseModel.Påminnelse(
                tidspunkt = HendelseModel.PåminnelseTidspunkt.Konkret(
                    LocalDateTime.parse("2020-01-01T01:01"),
                    Instant.parse("2020-01-01T01:01:01.00Z")
                ),
                eksterneVarsler = listOf(
                    SmsVarselKontaktinfo(
                        varselId = varselId,
                        tlfnr = "1234",
                        fnrEllerOrgnr = "32123",
                        smsTekst = "tekst",
                        sendevindu = EksterntVarselSendingsvindu.LØPENDE,
                        sendeTidspunkt = null,
                    )
                )
            ),
            sakId = null,
        ).also {
            repository.oppdaterModellEtterHendelse(it)
        }

        // ikke registret
        assertEquals(null, repository.findVarsel(varselId))
    }

    @Test
    fun `varsler i påminnelse blir registrert`() = withTestDatabase(EksternVarsling.databaseConfig) { database ->
        val repository = EksternVarslingRepository(database)

        val varselId = UUID.randomUUID()
        val notifikasjonId = UUID.randomUUID()
        HendelseModel.PåminnelseOpprettet(
            virksomhetsnummer = "1",
            notifikasjonId = notifikasjonId,
            hendelseId = UUID.randomUUID(),
            produsentId = "fager",
            kildeAppNavn = "local:local:local",
            opprettetTidpunkt = Instant.parse("2020-01-01T01:01:01.00Z"),
            fristOpprettetTidspunkt = Instant.parse("2020-01-01T01:01:01.00Z"),
            eksterneVarsler = listOf(
                SmsVarselKontaktinfo(
                    varselId = varselId,
                    tlfnr = "1234",
                    fnrEllerOrgnr = "32123",
                    smsTekst = "tekst",
                    sendevindu = EksterntVarselSendingsvindu.LØPENDE,
                    sendeTidspunkt = null,
                )
            ),
            frist = null,
            tidspunkt = HendelseModel.PåminnelseTidspunkt.Konkret(
                LocalDateTime.parse("2020-01-01T01:01"),
                Instant.parse("2020-01-01T01:01:01.00Z")
            ),
            bestillingHendelseId = notifikasjonId,
        ).also {
            repository.oppdaterModellEtterHendelse(it)
        }

        // varsel registrert
        assertEquals(
            EksternVarselTilstand.Ny(
                data = EksternVarselStatiskData(
                    varselId = varselId,
                    notifikasjonId = notifikasjonId,
                    produsentId = "fager",
                    eksternVarsel = EksternVarsel.Sms(
                        fnrEllerOrgnr = "32123",
                        sendeVindu = EksterntVarselSendingsvindu.LØPENDE,
                        sendeTidspunkt = null,
                        mobilnummer = "1234",
                        tekst = "tekst",
                        ordreId = null
                    ),
                )
            ),
            repository.findVarsel(varselId)
        )
    }

    @Test
    fun `Hard delete event for oppgave`() = withTestDatabase(EksternVarsling.databaseConfig) { database ->
        val repository = EksternVarslingRepository(database)

        val eksterntVarselVellykket = HendelseModel.EksterntVarselVellykket(
            virksomhetsnummer = "42",
            notifikasjonId = oppgaveOpprettet.aggregateId,
            hendelseId = UUID.randomUUID(),
            produsentId = "42",
            kildeAppNavn = "test:app",
            varselId = oppgaveOpprettet.eksterneVarsler[0].varselId,
            råRespons = NullNode.instance
        )
        val hardDelete = HendelseModel.HardDelete(
            virksomhetsnummer = "42",
            aggregateId = oppgaveOpprettet.aggregateId,
            hendelseId = UUID.randomUUID(),
            produsentId = "42",
            kildeAppNavn = "test:app",
            deletedAt = OffsetDateTime.now(),
            grupperingsid = null,
            merkelapp = oppgaveOpprettet.merkelapp,
        )

        repository.oppdaterModellEtterHendelse(oppgaveOpprettet)
        repository.oppdaterModellEtterHendelse(eksterntVarselVellykket)
        repository.oppdaterModellEtterHendelse(hardDelete)
        repository.oppdaterModellEtterHendelse(oppgaveOpprettet)
        repository.oppdaterModellEtterHendelse(eksterntVarselVellykket)
        repository.oppdaterModellEtterHendelse(hardDelete)

        // findVarsel feiler ikke
        assertNotNull(repository.findVarsel(eksterntVarselVellykket.varselId))
    }

    @Test
    fun `sendetidspunkt med localdatetime min`() = withTestDatabase(EksternVarsling.databaseConfig) { database ->
        val repository = EksternVarslingRepository(database)

        OppgaveOpprettet(
            virksomhetsnummer = "1",
            notifikasjonId = uuid("1"),
            hendelseId = uuid("2"),
            produsentId = "1",
            kildeAppNavn = "1",
            merkelapp = "1",
            eksternId = "1",
            mottakere = listOf(
                AltinnMottaker(
                    virksomhetsnummer = "1",
                    serviceCode = "1",
                    serviceEdition = "1"
                )
            ),
            tekst = "1",
            grupperingsid = null,
            lenke = "",
            opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01+00"),
            eksterneVarsler = listOf(
                SmsVarselKontaktinfo(
                    varselId = uuid("3"),
                    fnrEllerOrgnr = "1",
                    tlfnr = "1",
                    smsTekst = "hey",
                    sendevindu = EksterntVarselSendingsvindu.SPESIFISERT,
                    sendeTidspunkt = LocalDateTime.parse("-999999999-01-01T00:00")
                ),
            ),
            hardDelete = null,
            frist = null,
            påminnelse = null,
            sakId = null,
        ).also {
            repository.oppdaterModellEtterHendelse(it)
        }

        // job og varsel blir sendt fortløpende
        val id = repository.findJob(lockTimeout = Duration.ofMinutes(1))
        assertNotNull(id)
        val varsel = repository.findVarsel(id)
        assertNotNull(varsel)
        assertEquals(LocalDateTime.parse("-999999999-01-01T00:00"), varsel.data.eksternVarsel.sendeTidspunkt)
    }

    @Test
    fun `oppdatert kalenderavtale uten nye varsler påvirker ikke pending varsler`() = withTestDatabase(EksternVarsling.databaseConfig) { database ->
        val repository = EksternVarslingRepository(database)
        val varsel = SmsVarselKontaktinfo(
            varselId = uuid("3"),
            fnrEllerOrgnr = "1",
            tlfnr = "1",
            smsTekst = "hey",
            sendevindu = EksterntVarselSendingsvindu.LØPENDE,
            sendeTidspunkt = null
        )

        repository.oppdaterModellEtterHendelse(opprettet.copy(eksterneVarsler = listOf(varsel)))

        // varsler lagres med tilstand ny
        repository.findVarsel(varsel.varselId).let {
            it as EksternVarselTilstand.Ny
        }

        repository.oppdaterModellEtterHendelse(oppdatert.copy(eksterneVarsler = emptyList()))

        // varsler har forstatt tilstand ny
        repository.findVarsel(varsel.varselId).let {
            it as EksternVarselTilstand.Ny
        }
    }


    @Test
    fun `med nye varsler kansellerer pending varsler`() = withTestDatabase(EksternVarsling.databaseConfig) { database ->
        val repository = EksternVarslingRepository(database)
        val varsel = SmsVarselKontaktinfo(
            varselId = uuid("3"),
            fnrEllerOrgnr = "1",
            tlfnr = "1",
            smsTekst = "hey",
            sendevindu = EksterntVarselSendingsvindu.LØPENDE,
            sendeTidspunkt = null
        )

        repository.oppdaterModellEtterHendelse(opprettet.copy(eksterneVarsler = listOf(varsel)))

        // varsler lagres med tilstand ny
        repository.findVarsel(varsel.varselId).let {
            it as EksternVarselTilstand.Ny
        }

        val nyttVarsel = varsel.copy(varselId = uuid("4"))
        repository.oppdaterModellEtterHendelse(oppdatert.copy(eksterneVarsler = listOf(nyttVarsel)))

        // gamle varsler kanselleres
        repository.findVarsel(varsel.varselId).let {
            it as EksternVarselTilstand.Kansellert
        }
        // nye varsler lagres med tilstand ny
        repository.findVarsel(nyttVarsel.varselId).let {
            it as EksternVarselTilstand.Ny
        }
    }

    @Test
    fun `AVLYST kalenderavtale fører til kansellering av pending varsler`() = withTestDatabase(EksternVarsling.databaseConfig) { database ->
        val repository = EksternVarslingRepository(database)

        val varsel = SmsVarselKontaktinfo(
            varselId = uuid("3"),
            fnrEllerOrgnr = "1",
            tlfnr = "1",
            smsTekst = "hey",
            sendevindu = EksterntVarselSendingsvindu.LØPENDE,
            sendeTidspunkt = null
        )

        repository.oppdaterModellEtterHendelse(opprettet.copy(eksterneVarsler = listOf(varsel)))

        // varsler lagres med tilstand ny
        repository.findVarsel(varsel.varselId).let {
            it as EksternVarselTilstand.Ny
        }

        repository.oppdaterModellEtterHendelse(
            oppdatert.copy(
                eksterneVarsler = emptyList(),
                tilstand = HendelseModel.KalenderavtaleTilstand.AVLYST,
            )
        )

        // gamle varsler kanselleres
        repository.findVarsel(varsel.varselId).let {
            it as EksternVarselTilstand.Kansellert
        }
    }
}

private val opprettet = HendelseModel.KalenderavtaleOpprettet(
    virksomhetsnummer = "1",
    notifikasjonId = uuid("1"),
    hendelseId = uuid("1"),
    produsentId = "1",
    kildeAppNavn = "1",
    merkelapp = "1",
    eksternId = "1",
    mottakere = listOf(
        AltinnMottaker(
            virksomhetsnummer = "1",
            serviceCode = "1",
            serviceEdition = "1"
        )
    ),
    tekst = "1",
    grupperingsid = "42",
    lenke = "",
    opprettetTidspunkt = OffsetDateTime.parse("2020-01-01T01:01+00"),
    eksterneVarsler = emptyList(),
    hardDelete = null,
    påminnelse = null,
    sakId = uuid("1"),
    startTidspunkt = LocalDateTime.now(),
    sluttTidspunkt = null,
    erDigitalt = false,
    lokasjon = null,
    tilstand = HendelseModel.KalenderavtaleTilstand.VENTER_SVAR_FRA_ARBEIDSGIVER
)
private val oppdatert = HendelseModel.KalenderavtaleOppdatert(
    virksomhetsnummer = opprettet.virksomhetsnummer,
    notifikasjonId = opprettet.notifikasjonId,
    hendelseId = uuid("2"),
    produsentId = opprettet.produsentId,
    kildeAppNavn = opprettet.kildeAppNavn,
    merkelapp = opprettet.merkelapp,
    tekst = null,
    grupperingsid = opprettet.grupperingsid,
    lenke = null,
    opprettetTidspunkt = opprettet.opprettetTidspunkt.toInstant(),
    eksterneVarsler = emptyList(),
    hardDelete = null,
    påminnelse = null,
    startTidspunkt = null,
    sluttTidspunkt = null,
    erDigitalt = false,
    lokasjon = null,
    tilstand = HendelseModel.KalenderavtaleTilstand.ARBEIDSGIVER_HAR_GODTATT,
    idempotenceKey = null,
    oppdatertTidspunkt = Instant.now()
)