package no.nav.arbeidsgiver.notifikasjon.ekstern_varsling

import kotlinx.coroutines.test.runTest
import no.altinn.schemas.serviceengine.formsengine._2009._10.TransportType.EMAIL_PREFERRED
import no.altinn.schemas.services.serviceengine.notification._2015._06.NotificationResult
import no.altinn.schemas.services.serviceengine.notification._2015._06.SendNotificationResultList
import no.altinn.schemas.services.serviceengine.standalonenotificationbe._2009._10.StandaloneNotificationBEList
import no.altinn.schemas.services.serviceengine.standalonenotificationbe._2015._06.Service
import no.altinn.schemas.services.serviceengine.standalonenotificationbe._2022._11.StandaloneNotificationBEListV2
import no.altinn.services.common.fault._2009._10.AltinnFault
import no.altinn.services.serviceengine.notification._2010._10.INotificationAgencyExternalBasic
import no.altinn.services.serviceengine.notification._2010._10.INotificationAgencyExternalBasicSendStandaloneNotificationBasicV3AltinnFaultFaultFaultMessage
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.json.laxObjectMapper
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.texas.AuthClient
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.texas.TokenResponse
import org.apache.cxf.jaxws.JaxWsServerFactoryBean
import kotlin.test.*

class Altinn2VarselKlientImplTest {
    private val altinnEndpoint = "http://localhost:9999/NotificationAgencyExternalBasic.svc"
    val answers: MutableList<() -> SendNotificationResultList> = mutableListOf()
    val calls: MutableList<StandaloneNotificationBEList> = mutableListOf()
    private val server = JaxWsServerFactoryBean().apply {
        serviceClass = INotificationAgencyExternalBasic::class.java
        address = altinnEndpoint
        serviceBean = object : INotificationAgencyExternalBasic {
            override fun test() {
                TODO("Not yet implemented")
            }

            override fun sendStandaloneNotificationBasic(
                systemUserName: String?,
                systemPassword: String?,
                standaloneNotifications: StandaloneNotificationBEList?
            ) {
                TODO("Not yet implemented")
            }

            override fun sendStandaloneNotificationBasicV2(
                systemUserName: String?,
                systemPassword: String?,
                standaloneNotifications: StandaloneNotificationBEList?
            ): String {
                TODO("Not yet implemented")
            }

            override fun sendStandaloneNotificationBasicV3(
                systemUserName: String?,
                systemPassword: String?,
                standaloneNotifications: StandaloneNotificationBEList
            ): SendNotificationResultList {
                calls.add(standaloneNotifications)
                return answers.removeAt(0).invoke()
            }

            override fun sendStandaloneNotificationBasicV4(
                systemUserName: String?,
                systemPassword: String?,
                standaloneNotifications: StandaloneNotificationBEListV2?
            ): SendNotificationResultList {
                TODO("Not yet implemented")
            }
        }
    }.create()

    private val klient = Altinn2VarselKlientImpl(
        altinnEndPoint = altinnEndpoint,
        authClient = object : AuthClient {
            override suspend fun token(target: String) = TokenResponse.Success("", 3600)
            override suspend fun exchange(target: String, userToken: String) = TODO("Not yet implemented")
            override suspend fun introspect(accessToken: String) = TODO("Not yet implemented")
        }
    )

    // TODO: optimization, use @BeforeAll
    @BeforeTest
    fun setUp() {
        server.start()
    }

    @AfterTest
    fun tearDown() {
        server.stop()
    }

    @Test
    fun `returnerer ok for vellykket altinntjeneste kall`() = runTest {
        val withNotificationResult = SendNotificationResultList().apply {
            notificationResult.add(NotificationResult())
        }
        answers.add { withNotificationResult }

        val response = klient.send(
            EksternVarsel.Altinntjeneste(
                fnrEllerOrgnr = "9876543210",
                sendeVindu = HendelseModel.EksterntVarselSendingsvindu.LØPENDE,
                sendeTidspunkt = null,
                serviceCode = "1337",
                serviceEdition = "42",
                tittel = "foo",
                innhold = "bar",
            )
        )

        response as AltinnVarselKlientResponse.Ok
        assertEquals(laxObjectMapper.valueToTree(withNotificationResult), response.rå)
        with(calls.removeLast()) {
            this as StandaloneNotificationBEList
            assertEquals(1, standaloneNotification.size)
            assertEquals(1044, standaloneNotification[0].languageID)
            assertEquals("TokenTextOnly", standaloneNotification[0].notificationType.value)
            assertEquals("9876543210", standaloneNotification[0].reporteeNumber.value)
            assertEquals("1337", (standaloneNotification[0].service.value as Service).serviceCode)
            assertEquals(42, (standaloneNotification[0].service.value as Service).serviceEdition)
            assertEquals(
                EMAIL_PREFERRED,
                standaloneNotification[0].receiverEndPoints.value.receiverEndPoint[0].transportType.value
            )
            assertEquals("foo", standaloneNotification[0].textTokens.value.textToken[0].tokenValue.value)
            assertEquals("bar", standaloneNotification[0].textTokens.value.textToken[1].tokenValue.value)
            assertEquals("ikke-svar@nav.no", standaloneNotification[0].fromAddress.value)
        }
    }

    @Test
    fun `returnerer ok for kall med AltinnFault`() = runTest {
        val faultOF = no.altinn.services.common.fault._2009._10.ObjectFactory()
        val altinnFault = AltinnFault().apply {
            errorID = 30010
            errorGuid = faultOF.createAltinnFaultErrorGuid("uuid")
            userId = faultOF.createAltinnFaultUserId("oof")
            userGuid = faultOF.createAltinnFaultUserGuid("uuid")
            altinnErrorMessage = faultOF.createAltinnFaultAltinnErrorMessage("fubar")
            altinnExtendedErrorMessage = faultOF.createAltinnFaultAltinnExtendedErrorMessage("this api")
            altinnLocalizedErrorMessage = faultOF.createAltinnFaultAltinnLocalizedErrorMessage("my head")
        }
        val ex = INotificationAgencyExternalBasicSendStandaloneNotificationBasicV3AltinnFaultFaultFaultMessage(
            "faultfaultfaustmessagemassage",
            altinnFault
        )
        answers.add { throw ex }

        val response = klient.send(
            EksternVarsel.Altinntjeneste(
                fnrEllerOrgnr = "9876543210",
                sendeVindu = HendelseModel.EksterntVarselSendingsvindu.LØPENDE,
                sendeTidspunkt = null,
                serviceCode = "1337",
                serviceEdition = "42",
                tittel = "foo",
                innhold = "bar",
            )
        )

        response as AltinnVarselKlientResponse.Feil
        with(response) {
            assertEquals(altinnFault.errorID.toString(), feilkode)
            assertEquals(altinnFault.altinnErrorMessage.value, feilmelding)
            assertNotNull(rå)
        }
    }

    @Test
    fun `returnerer feil for kall med generell feil`() = runTest {
        answers.add { throw Exception("oof") }

        val response = klient.send(
            EksternVarsel.Altinntjeneste(
                fnrEllerOrgnr = "9876543210",
                sendeVindu = HendelseModel.EksterntVarselSendingsvindu.LØPENDE,
                sendeTidspunkt = null,
                serviceCode = "1337",
                serviceEdition = "42",
                tittel = "foo",
                innhold = "bar",
            )
        )

        response as UkjentException
    }

    /**
     * TAG-2054
     */
    @Test
    fun `returnerer feil for teknisk feil som bør fosøkes på nytt`() = runTest {
        val feilkode = 44
        val faultOF = no.altinn.services.common.fault._2009._10.ObjectFactory()
        val altinnFault = AltinnFault().apply {
            errorID = feilkode
            errorGuid = faultOF.createAltinnFaultErrorGuid("uuid")
            userId = faultOF.createAltinnFaultUserId("oof")
            userGuid = faultOF.createAltinnFaultUserGuid("uuid")
            altinnErrorMessage = faultOF.createAltinnFaultAltinnErrorMessage("fubar")
            altinnExtendedErrorMessage = faultOF.createAltinnFaultAltinnExtendedErrorMessage("this api")
            altinnLocalizedErrorMessage = faultOF.createAltinnFaultAltinnLocalizedErrorMessage("my head")
        }
        val ex = INotificationAgencyExternalBasicSendStandaloneNotificationBasicV3AltinnFaultFaultFaultMessage(
            "faultfaultfaustmessagemassage",
            altinnFault
        )
        answers.add { throw ex }

        val response = klient.send(
            EksternVarsel.Altinntjeneste(
                fnrEllerOrgnr = "9876543210",
                sendeVindu = HendelseModel.EksterntVarselSendingsvindu.LØPENDE,
                sendeTidspunkt = null,
                serviceCode = "1337",
                serviceEdition = "42",
                tittel = "foo",
                innhold = "bar",
            )
        )

        response as AltinnVarselKlientResponse.Feil
        assertEquals(true, response.isRetryable())
    }
}
