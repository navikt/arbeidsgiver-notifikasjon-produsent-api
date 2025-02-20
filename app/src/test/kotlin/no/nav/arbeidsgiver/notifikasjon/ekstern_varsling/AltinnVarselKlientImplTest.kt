package no.nav.arbeidsgiver.notifikasjon.ekstern_varsling

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.instanceOf
import no.altinn.schemas.serviceengine.formsengine._2009._10.TransportType.*
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

class AltinnVarselKlientImplTest : DescribeSpec({
    val altinnEndpoint = "http://localhost:9999/NotificationAgencyExternalBasic.svc"
    val answers: MutableList<() -> SendNotificationResultList> = mutableListOf()
    val calls: MutableList<StandaloneNotificationBEList> = mutableListOf()
    val server = JaxWsServerFactoryBean().apply {
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

    val klient = AltinnVarselKlientImpl(
        altinnEndPoint = altinnEndpoint,
        authClient = object : AuthClient {
            override suspend fun token(target: String) = TokenResponse.Success("", 3600)
            override suspend fun exchange(target: String, userToken: String) = TODO("Not yet implemented")
            override suspend fun introspect(accessToken: String) = TODO("Not yet implemented")
        }
    )

    beforeSpec {
        server.start()
    }

    afterSpec {
        server.stop()
    }

    describe("returnerer ok for vellykket sms kall") {
        val withNotificationResult = SendNotificationResultList().apply {
            notificationResult.add(NotificationResult())
        }
        answers.add { withNotificationResult }

        val response = klient.send(
            EksternVarsel.Sms(
                fnrEllerOrgnr = "9876543210",
                sendeVindu = HendelseModel.EksterntVarselSendingsvindu.LØPENDE,
                sendeTidspunkt = null,
                mobilnummer = "12341234",
                tekst = "foo",
            )
        )

        response shouldBe instanceOf<AltinnVarselKlientResponse.Ok>()
        response as AltinnVarselKlientResponse.Ok
        response.rå shouldBe laxObjectMapper.valueToTree(withNotificationResult)
        calls.removeLast().let {
            it shouldBe instanceOf<StandaloneNotificationBEList>()
            it.standaloneNotification.size shouldBe 1
            it.standaloneNotification[0].languageID shouldBe 1044
            it.standaloneNotification[0].notificationType.value shouldBe "TokenTextOnly"
            it.standaloneNotification[0].reporteeNumber.value shouldBe "9876543210"
            it.standaloneNotification[0].receiverEndPoints.value.receiverEndPoint[0].transportType.value shouldBe SMS
            it.standaloneNotification[0].receiverEndPoints.value.receiverEndPoint[0].receiverAddress.value shouldBe "12341234"
            it.standaloneNotification[0].textTokens.value.textToken[0].tokenValue.value shouldBe "foo"
            it.standaloneNotification[0].textTokens.value.textToken[1].tokenValue.value shouldBe ""
            it.standaloneNotification[0].useServiceOwnerShortNameAsSenderOfSms.value shouldBe true
        }
    }

    describe("returnerer ok for vellykket epost kall") {
        val withNotificationResult = SendNotificationResultList().apply {
            notificationResult.add(NotificationResult())
        }
        answers.add { withNotificationResult }

        val response = klient.send(
            EksternVarsel.Epost(
                fnrEllerOrgnr = "9876543210",
                sendeVindu = HendelseModel.EksterntVarselSendingsvindu.LØPENDE,
                sendeTidspunkt = null,
                epostadresse = "donald@duck.co",
                tittel = "foo",
                body = "bar",
            )
        )

        response shouldBe instanceOf<AltinnVarselKlientResponse.Ok>()
        response as AltinnVarselKlientResponse.Ok
        response.rå shouldBe laxObjectMapper.valueToTree(withNotificationResult)
        calls.removeLast().let {
            it shouldBe instanceOf<StandaloneNotificationBEList>()
            it.standaloneNotification.size shouldBe 1
            it.standaloneNotification[0].languageID shouldBe 1044
            it.standaloneNotification[0].notificationType.value shouldBe "TokenTextOnly"
            it.standaloneNotification[0].reporteeNumber.value shouldBe "9876543210"
            it.standaloneNotification[0].receiverEndPoints.value.receiverEndPoint[0].transportType.value shouldBe EMAIL
            it.standaloneNotification[0].receiverEndPoints.value.receiverEndPoint[0].receiverAddress.value shouldBe "donald@duck.co"
            it.standaloneNotification[0].textTokens.value.textToken[0].tokenValue.value shouldBe "foo"
            it.standaloneNotification[0].textTokens.value.textToken[1].tokenValue.value shouldBe "bar"
            it.standaloneNotification[0].fromAddress.value shouldBe "ikke-svar@nav.no"
        }
    }

    describe("returnerer ok for vellykket altinntjeneste kall") {
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

        response shouldBe instanceOf<AltinnVarselKlientResponse.Ok>()
        response as AltinnVarselKlientResponse.Ok
        response.rå shouldBe laxObjectMapper.valueToTree(withNotificationResult)
        calls.removeLast().let {
            it shouldBe instanceOf<StandaloneNotificationBEList>()
            it.standaloneNotification.size shouldBe 1
            it.standaloneNotification[0].languageID shouldBe 1044
            it.standaloneNotification[0].notificationType.value shouldBe "TokenTextOnly"
            it.standaloneNotification[0].reporteeNumber.value shouldBe "9876543210"
            (it.standaloneNotification[0].service.value as Service).serviceCode shouldBe "1337"
            (it.standaloneNotification[0].service.value as Service).serviceEdition shouldBe 42
            it.standaloneNotification[0].receiverEndPoints.value.receiverEndPoint[0].transportType.value shouldBe EMAIL_PREFERRED
            it.standaloneNotification[0].textTokens.value.textToken[0].tokenValue.value shouldBe "foo"
            it.standaloneNotification[0].textTokens.value.textToken[1].tokenValue.value shouldBe "bar"
            it.standaloneNotification[0].fromAddress.value shouldBe "ikke-svar@nav.no"
        }
    }

    describe("returnerer ok for kall med AltinnFault") {
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
            EksternVarsel.Sms(
                fnrEllerOrgnr = "",
                sendeVindu = HendelseModel.EksterntVarselSendingsvindu.LØPENDE,
                sendeTidspunkt = null,
                mobilnummer = "",
                tekst = "",
            )
        )

        response shouldBe instanceOf<AltinnVarselKlientResponse.Feil>()
        response as AltinnVarselKlientResponse.Feil
        response.let {
            it.feilkode shouldBe altinnFault.errorID.toString()
            it.feilmelding shouldBe altinnFault.altinnErrorMessage.value
            it.rå shouldNotBe null
        }
    }

    describe("returnerer feil for kall med generell feil") {
        answers.add { throw Exception("oof") }

        val response = klient.send(
            EksternVarsel.Sms(
                fnrEllerOrgnr = "",
                sendeVindu = HendelseModel.EksterntVarselSendingsvindu.LØPENDE,
                sendeTidspunkt = null,
                mobilnummer = "",
                tekst = "",
            )
        )

        response shouldBe instanceOf<UkjentException>()
    }

    /**
     * TAG-2054
     */
    describe("returnerer feil for teknisk feil som bør fosøkes på nytt") {
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
            EksternVarsel.Sms(
                fnrEllerOrgnr = "",
                sendeVindu = HendelseModel.EksterntVarselSendingsvindu.LØPENDE,
                sendeTidspunkt = null,
                mobilnummer = "",
                tekst = "",
            )
        )

        response shouldBe instanceOf<AltinnVarselKlientResponse.Feil>()
        response as AltinnVarselKlientResponse.Feil
        response.isRetryable() shouldBe true
    }
})
