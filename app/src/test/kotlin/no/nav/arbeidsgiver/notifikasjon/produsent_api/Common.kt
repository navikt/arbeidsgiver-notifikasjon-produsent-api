package no.nav.arbeidsgiver.notifikasjon.produsent_api

import io.ktor.server.testing.*
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.GraphQLRequest
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.NærmesteLederDefinisjon
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.Produsent
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.ProdusentRegister
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.ServicecodeDefinisjon
import no.nav.arbeidsgiver.notifikasjon.util.PRODUSENT_HOST
import no.nav.arbeidsgiver.notifikasjon.util.TOKENDINGS_TOKEN
import no.nav.arbeidsgiver.notifikasjon.util.post
import org.intellij.lang.annotations.Language

fun TestApplicationEngine.produsentApi(req: GraphQLRequest): TestApplicationResponse {
    return post(
        "/api/graphql",
        host = PRODUSENT_HOST,
        jsonBody = req,
        accept = "application/json",
        authorization = "Bearer $TOKENDINGS_TOKEN"
    )
}

fun TestApplicationEngine.produsentApi(
    @Language("GraphQL") req: String
): TestApplicationResponse {
    return produsentApi(GraphQLRequest(req))
}

val mockProdusentRegister: ProdusentRegister = object: ProdusentRegister {
    override fun finn(subject: String): Produsent {
        return Produsent(
            accessPolicy = listOf("someproducer"),
            tillatteMerkelapper = listOf("tag"),
            tillatteMottakere = listOf(
                ServicecodeDefinisjon(code = "5441", version = "1"),
                NærmesteLederDefinisjon,
            )
        )
    }

    override fun validateAll() {
    }
}

