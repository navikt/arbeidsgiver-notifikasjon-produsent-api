package no.nav.arbeidsgiver.notifikasjon.ekstern_varsling

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.instanceOf
import no.altinn.schemas.services.serviceengine.notification._2015._06.NotificationResult
import no.altinn.schemas.services.serviceengine.notification._2015._06.SendNotificationResultList
import no.altinn.schemas.services.serviceengine.standalonenotificationbe._2009._10.StandaloneNotificationBEList
import no.altinn.services.common.fault._2009._10.AltinnFault
import no.altinn.services.serviceengine.notification._2010._10.INotificationAgencyExternalBasic
import no.altinn.services.serviceengine.notification._2010._10.INotificationAgencyExternalBasicSendStandaloneNotificationBasicV3AltinnFaultFaultFaultMessage
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.azuread.AzureService
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.json.laxObjectMapper
import org.apache.cxf.jaxws.JaxWsServerFactoryBean

class AltinnVarselKlientImplTest : DescribeSpec({
    val altinnEndpoint = "http://localhost:9999/NotificationAgencyExternalBasic.svc"
    val answers: MutableList<() -> SendNotificationResultList> = mutableListOf()
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
                standaloneNotifications: StandaloneNotificationBEList?
            ): SendNotificationResultList {
                return answers.removeAt(0).invoke()
            }
        }
    }.create()

    val klient = AltinnVarselKlientImpl(
        altinnEndPoint = altinnEndpoint,
        azureService = object : AzureService {
            override suspend fun getAccessToken(targetApp: String) = ""
        }
    )

    beforeSpec {
        server.start()
    }

    afterSpec {
        server.stop()
    }

    describe("returnerer ok for vellykket kall") {
        val withNotificationResult = SendNotificationResultList().withNotificationResult(NotificationResult())
        answers.add { withNotificationResult }

        val response = klient.send(
            EksternVarsel.Sms(
                fnrEllerOrgnr = "",
                sendeVindu = HendelseModel.EksterntVarselSendingsvindu.LØPENDE,
                sendeTidspunkt = null,
                mobilnummer = "",
                tekst = "",
            )
        )

        response.isSuccess shouldBe true
        response.getOrNull() shouldBe instanceOf<AltinnVarselKlient.AltinnResponse.Ok>()
        response.getOrNull()!!.rå shouldBe laxObjectMapper.valueToTree(withNotificationResult)
    }

    describe("returnerer ok for kall med AltinnFault") {
        val faultOF = no.altinn.services.common.fault._2009._10.ObjectFactory()
        val altinnFault = AltinnFault()
            .withErrorID(0)
            .withErrorGuid(faultOF.createAltinnFaultErrorGuid("uuid"))
            .withUserId(faultOF.createAltinnFaultUserId("oof"))
            .withUserGuid(faultOF.createAltinnFaultUserGuid("uuid"))
            .withAltinnErrorMessage(faultOF.createAltinnFaultAltinnErrorMessage("fubar"))
            .withAltinnExtendedErrorMessage(faultOF.createAltinnFaultAltinnExtendedErrorMessage("this api"))
            .withAltinnLocalizedErrorMessage(faultOF.createAltinnFaultAltinnLocalizedErrorMessage("my head"))
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

        response.isSuccess shouldBe true
        response.getOrNull() shouldBe instanceOf<AltinnVarselKlient.AltinnResponse.Feil>()
        (response.getOrNull() as AltinnVarselKlient.AltinnResponse.Feil).let {
            it.feilkode shouldBe altinnFault.errorID.toString()
            it.feilmelding shouldBe altinnFault.altinnErrorMessage.value
            it.rå shouldNotBe null
        }
    }
})
