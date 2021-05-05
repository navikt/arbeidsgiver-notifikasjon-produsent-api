package no.nav.arbeidsgiver.notifikasjon

import io.kotest.core.spec.style.DescribeSpec
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.apache.kafka.clients.producer.Producer

class KlikkPåNotifikasjonGraphQLTest: DescribeSpec( {
//    describe("bruker-api: rapporterer om at notifikasjon er klikket på") {
//        beforeEach {
//            mockkObject(QueryModelRepository)
//            mockkStatic(Producer<*, *>::sendEvent)
//        }
//
//        afterEach {
//            unmockkObject(QueryModelRepository)
//            unmockkStatic()
//        }
//        val mockedProducer = null
//
//        context("uklikket-notifikasjon eksisterer for bruker") {
//            val fnr = "12345"
//            val id = "4321"
//
//            query = """
//                    mutation {
//                        notifikasjonKlikketPaa(id: "$id") {
//                            errors {
//                                feilmelding
//                            }
//                        }
//                    }
//                """.trimIndent()
//
//            it("får ingen errors i response") {
//            }
//
//            it("backenden gjør") {
//                verify {
//                    QueryModelRepository.registereKlikkPåNotifikasjon(fnr, id)
//                }
//            }
//
//            it("backend") {
//                verify {
//                    mockedPRoducer.sendEventKlikkPåNotifikasjon(fnr, id)
//                }
//            }
//        }
//    }
})