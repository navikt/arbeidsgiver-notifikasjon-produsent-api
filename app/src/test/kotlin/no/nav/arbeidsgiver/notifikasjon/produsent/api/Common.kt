package no.nav.arbeidsgiver.notifikasjon.produsent.api

import io.ktor.client.*
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.GraphQLRequest
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.*
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepository
import no.nav.arbeidsgiver.notifikasjon.util.fakeProdusentApiOboToken
import no.nav.arbeidsgiver.notifikasjon.util.PRODUSENT_HOST
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
                ServicecodeDefinisjon(code = "5441", version = "1"),
                ServicecodeDefinisjon(code = "1", version = "1"),
                RessursIdDefinisjon(ressursId = "test-fager"),
                NærmesteLederDefinisjon,
            )
        )
    }
}

suspend fun ProdusentRepository.oppdaterModellEtterHendelse(hendelse: HendelseModel.Hendelse){
    oppdaterModellEtterHendelse(hendelse, HendelseModel.HendelseMetadata(Instant.parse("1970-01-01T00:00:00Z")))
}

