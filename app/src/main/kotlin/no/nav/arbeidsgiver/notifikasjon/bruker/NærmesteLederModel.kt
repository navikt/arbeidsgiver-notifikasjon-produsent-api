package no.nav.arbeidsgiver.notifikasjon.bruker

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.JsonDeserializer
import java.time.LocalDate
import java.util.*

interface NærmesteLederModel {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class NarmesteLederLeesah(
        val narmesteLederId: UUID,
        val fnr: String,
        val narmesteLederFnr: String,
        val orgnummer: String,
        val aktivTom: LocalDate?,
//        utkommentert mtp dataminimering
//        val narmesteLederTelefonnummer: String,
//        val narmesteLederEpost: String,
//        val aktivFom: LocalDate,
//        val arbeidsgiverForskutterer: Boolean?,
//        val timestamp: OffsetDateTime
    )

    data class NærmesteLederFor(
        val ansattFnr: String,
        val virksomhetsnummer: String,
    )

    class NarmesteLederLeesahDeserializer : JsonDeserializer<NarmesteLederLeesah>(NarmesteLederLeesah::class.java)

    suspend fun hentAnsatte(
        narmesteLederFnr: String,
    ): List<NærmesteLederFor>

    suspend fun oppdaterModell(nærmesteLederLeesah: NarmesteLederLeesah)
}

class NærmesteLederModelImpl(
    private val database: Database,
) : NærmesteLederModel {

    override suspend fun hentAnsatte(narmesteLederFnr: String): List<NærmesteLederModel.NærmesteLederFor> {
        return database.runNonTransactionalQuery(
            """
            select * from naermeste_leder_kobling 
                where naermeste_leder_fnr = ?
            """, {
                string(narmesteLederFnr)
            }) {
            NærmesteLederModel.NærmesteLederFor(
                ansattFnr = getString("fnr"),
                virksomhetsnummer = getString("orgnummer"),
            )
        }
    }


    override suspend fun oppdaterModell(nærmesteLederLeesah: NærmesteLederModel.NarmesteLederLeesah) {
        if (nærmesteLederLeesah.aktivTom != null) {
            database.nonTransactionalCommand(
                """
                delete from naermeste_leder_kobling where id = ?
            """
            ) {
                uuid(nærmesteLederLeesah.narmesteLederId)
            }
        } else {
            database.nonTransactionalCommand(
                """
                INSERT INTO naermeste_leder_kobling(id, fnr, naermeste_leder_fnr, orgnummer)
                VALUES(?, ?, ?, ?) 
                ON CONFLICT (id) 
                DO 
                UPDATE SET 
                    orgnummer = EXCLUDED.orgnummer, 
                    fnr = EXCLUDED.fnr, 
                    naermeste_leder_fnr = EXCLUDED.naermeste_leder_fnr;
            """
            ) {
                uuid(nærmesteLederLeesah.narmesteLederId)
                string(nærmesteLederLeesah.fnr)
                string(nærmesteLederLeesah.narmesteLederFnr)
                string(nærmesteLederLeesah.orgnummer)
            }
        }
    }
}
