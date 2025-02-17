package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.PåminnelseTidspunkt
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.local_database.getInstant
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import java.time.Instant
import java.util.*

class SakOppdateresTests : DescribeSpec({
    val database = testDatabase(Bruker.databaseConfig, SakOppdateresTests::class.simpleName)
    val brukerRepository = BrukerRepositoryImpl(database)

    suspend fun hentSakSisteEndretTidspunktById(sakId: UUID): Instant {
        val sakSistEndret = database.nonTransactionalExecuteQuery(
            """
                select sist_endret_tidspunkt
                from sak
                where id = ?
                
            """.trimIndent(),
            {
                uuid(sakId)
            },
            {
                getInstant("sist_endret_tidspunkt")
            }
        ).first()
        return sakSistEndret
    }

    describe("Sak oppdateres") {
        val sakOpprettet = brukerRepository.sakOpprettet(
            grupperingsid = "grupperingsid",
            merkelapp = "merkelapp",
            )

        it("på OppgaveOpprettet") {
            val oppgave = brukerRepository.oppgaveOpprettet(sak = sakOpprettet)
            val sistEndretTidspunkt = hentSakSisteEndretTidspunktById(sakOpprettet.sakId)
            sistEndretTidspunkt shouldBe oppgave.opprettetTidspunkt.toInstant()
        }
        it("på BeskjedOpprettet") {
            val beskjed = brukerRepository.beskjedOpprettet(sak = sakOpprettet)
            val sistEndretTidspunkt = hentSakSisteEndretTidspunktById(sakOpprettet.sakId)
            sistEndretTidspunkt shouldBe beskjed.opprettetTidspunkt.toInstant()
        }
        it("på KalenderavtaleOpprettet") {
            val kalenderavtale = brukerRepository.kalenderavtaleOpprettet(sak = sakOpprettet)
            val sistEndretTidspunkt = hentSakSisteEndretTidspunktById(sakOpprettet.sakId)
            sistEndretTidspunkt shouldBe kalenderavtale.opprettetTidspunkt.toInstant()
        }
        it("på NyStatusSak") {
            val statusSak = brukerRepository.nyStatusSak(sak = sakOpprettet, idempotensKey = "nei")
            val sistEndretTidspunkt = hentSakSisteEndretTidspunktById(sakOpprettet.sakId)
            sistEndretTidspunkt shouldBe statusSak.opprettetTidspunkt.toInstant()
        }
        it("på PåminnelseOpprettet") {
            val oppgave = brukerRepository.oppgaveOpprettet(sak = sakOpprettet)
            val påminnelsesTidspunkt = oppgave.opprettetTidspunkt.plusDays(7)
            val påminnelse = brukerRepository.påminnelseOpprettet(
                oppgave,
                tidspunkt = PåminnelseTidspunkt.createAndValidateKonkret(
                    påminnelsesTidspunkt.toLocalDateTime(),
                    oppgave.opprettetTidspunkt,
                    null,
                    null
                )
            )
            val sistEndretTidspunkt = hentSakSisteEndretTidspunktById(sakOpprettet.sakId)
            sistEndretTidspunkt shouldBe påminnelse.tidspunkt.påminnelseTidspunkt
        }
    }
})

