package no.nav.arbeidsgiver.notifikasjon.produsent.api

import io.ktor.client.*
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.GraphQLRequest
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.NærmesteLederDefinisjon
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.Produsent
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.ProdusentRegister
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.RessursIdDefinisjon
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepository
import no.nav.arbeidsgiver.notifikasjon.util.PRODUSENT_HOST
import no.nav.arbeidsgiver.notifikasjon.util.fakeProdusentApiOboToken
import no.nav.arbeidsgiver.notifikasjon.util.post
import org.intellij.lang.annotations.Language
import java.time.Instant

suspend fun HttpClient.produsentApi(req: GraphQLRequest) = post(
    "/api/graphql",
    host = PRODUSENT_HOST,
    jsonBody = req,
    accept = "application/json",
    authorization = "Bearer ${fakeProdusentApiOboToken()}"
)

suspend fun HttpClient.produsentApi(
    @Language("GraphQL") req: String,
) = produsentApi(GraphQLRequest(req))

val stubProdusentRegister: ProdusentRegister = object : ProdusentRegister {
    override fun finn(appName: String): Produsent {
        return Produsent(
            id = "someproducer",
            accessPolicy = listOf("someproducer"),
            tillatteMerkelapper = listOf("tag", "tag2"),
            tillatteMottakere = listOf(
                RessursIdDefinisjon(ressursId = "test-fager"),
                RessursIdDefinisjon(ressursId = "nav_arbeidsforhold_aa-registeret-innsyn-arbeidsgiver"),
                RessursIdDefinisjon(ressursId = "nav_foreldrepenger_inntektsmelding"),
                RessursIdDefinisjon(ressursId = "nav_sykepenger_inntektsmelding"),
                RessursIdDefinisjon(ressursId = "nav_sykdom-i-familien_inntektsmelding"),
                NærmesteLederDefinisjon,
            )
        )
    }
}

suspend fun ProdusentRepository.oppdaterModellEtterHendelse(hendelse: HendelseModel.Hendelse){
    oppdaterModellEtterHendelse(hendelse, HendelseModel.HendelseMetadata(Instant.parse("1970-01-01T00:00:00Z")))
}

