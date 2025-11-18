package no.nav.arbeidsgiver.notifikasjon.ekstern_varsling

import io.ktor.http.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.launch
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database.Companion.openDatabaseAndSetReady
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.basedOnEnv
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.configureRouting
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.http.registerShutdownListener
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.json.laxObjectMapper
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.HendelsesstrømKafkaImpl
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.NOTIFIKASJON_TOPIC
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.lagKafkaHendelseProdusent


object EksternVarsling {
    val databaseConfig = Database.config("ekstern_varsling_model")

    fun main(httpPort: Int = 8080) {
        embeddedServer(CIO, configure = {
            connector {
                port = httpPort
            }
            shutdownGracePeriod = 20000
            shutdownTimeout = 30000
        }) {
            val hendelseProdusent = lagKafkaHendelseProdusent(topic = NOTIFIKASJON_TOPIC)
            val database = openDatabaseAndSetReady(databaseConfig)
            val eksternVarslingRepository = EksternVarslingRepository(database)

            val hendelsestrøm = HendelsesstrømKafkaImpl(
                topic = NOTIFIKASJON_TOPIC,
                groupId = "ekstern-varsling-model-builder",
                replayPeriodically = false,
            )
            val exporterHendelsestrøm = HendelsesstrømKafkaImpl(
                topic = NOTIFIKASJON_TOPIC,
                groupId = "ekstern-varsling-status-exporter",
                replayPeriodically = false,
            )
            val rebuilder = HendelsesstrømKafkaImpl(
                topic = NOTIFIKASJON_TOPIC,
                groupId = "ekstern-varsling-model-rebuild-18112025",
                replayPeriodically = false,
            )


            /**
             * patch update missing error codes causing stuck consumers - remove when ajour
             */
            launch {
                val updateNeeded = listOf(
                    "5ff53dc3-58e8-423b-9dac-64378cd71376",
                    "eed657ef-4759-46c1-b55e-7d644afa1043",
                    "8290af87-11c0-4a0b-bc8f-5fd9385febf5",
                    "ba237984-0ada-466a-8956-4b551d417418",
                    "07077478-b715-42ec-b6b5-00ab738d3873",
                    "66e9169d-80fe-4b01-a7fb-e1f40a23591e",
                    "8a788745-3d7a-48a3-9325-dea50e7b605d",
                    "a74244c1-94c4-49a1-9b9f-57b798c3a36c",
                    "1dd0a910-cd14-4a71-8747-52c3060d4735",
                    "40845747-4ab5-423f-beff-8fa3a8d8584e",
                    "d02502a0-3404-40f0-872a-2d673eeb22e1",
                    "e0b62ab6-2151-48e0-bcab-fdd67f264da5",
                    "0d5b552c-808a-4e4f-bdd9-1bf8b9136312",
                    "e5291ea7-5b96-4c4e-842c-12ae7ffdc9ee",
                    "14c0a884-abe5-4d46-a9cb-edc74988ed64",
                    "8c9b61e9-2d9e-4e3b-a0fc-dc1949bf05b5",
                    "b90b57dc-05c2-4021-aaee-a031d5c519e7",
                    "54ed2567-00aa-4501-8eee-fef16cd955d6",
                    "6db48a9b-42eb-471a-8773-614afefabe2c",
                    "1f5f1a78-2bd7-4196-ba3e-f10af3785da5",
                    "81c4289f-288b-4f6e-a224-dbe129eb41bc",
                    "621f5536-2ad7-4ed4-abbd-d644d13b018b",
                    "79872eb6-8e4d-46d3-94bc-60ce7abc76ef",
                    "9f0d76ba-cbd9-4af1-bda6-a1b7f3ce4d51",
                    "ad78c0ae-d776-4567-9ae6-d2be0b41569b",
                    "0aee3c4d-d828-485f-b147-63a6f59ce9fb",
                    "2b9f6e27-8b82-4eec-9571-da622f6a046b",
                    "4f12bcc3-f1d6-46d0-83b8-f17fa79b756c",
                    "001e0bb0-2d35-46f8-a9e6-d38519aef81b",
                    "ee2ca26d-19c7-4e52-89d8-945ce172483f",
                    "9bc2607d-cad1-4f53-a35b-ca3ac35c8416",
                    "47af1ef7-e697-4b7f-8453-db7407124f6f",
                    "6d9806f4-11c7-4697-9a41-951939a4769e",
                    "7a66a3f9-692d-4264-856f-ca5c18a6c641",
                    "26a531b1-837b-40d0-9b23-b3b02b195270",
                    "3ec9d3f2-10e9-4a35-bc05-5d63340d350f",
                    "0a44ecb6-3d63-4035-afd2-f9712739cf40",
                    "d32fd750-8584-4c5d-954b-8bd5277cbc8c",
                    "85854c6e-cfc3-4c01-ab84-9fb63ae73ff8",
                    "8ed2710f-b7ac-4880-a260-ad8cc4e77694",
                    "4c31f388-a6cc-4768-9c37-7df886a0ca98",
                    "e94c6bb4-8174-4adf-bc50-4bbc4d807fd9",
                    "22eb7cac-e649-4fb8-baa8-cc60804cbff4",
                    "aa6b6328-77c1-450a-a264-f1597985e3d6",
                    "597058e0-f753-412f-920d-ad9f462aa965",
                    "ad5f825a-f7db-4154-bf21-ec98cbd9b5ba",
                    "7ea9d0fc-e7d6-4a0a-8522-33c958abff20",
                    "572d6544-613f-4cd6-8d51-1fe1db2cd477",
                    "d70c2285-9488-45f8-90bf-6ae37e5f7681",
                    "b84cf1c2-208a-4111-8806-c595aedc17ce",
                    "9516e8da-9d8a-4f72-ad28-b015ca9a0e63",
                    "87728972-d6a2-4635-81e5-c0c978eeab3e",
                    "7d7e54e3-9c7c-41a6-b30d-7bea37aeb5b8",
                    "03229f9c-cbd0-417e-bc7d-91160fe74472",
                    "4749bc10-aa49-41ed-a2b6-ffae1abac27b",
                    "88660182-9056-4ac1-8115-6413cd6d20bc",
                    "36e91821-4516-4120-b4bf-cb8f413411fe",
                    "d60217ec-e609-4c55-95c8-e0a1049ac196",
                    "dfdc8c1f-d625-49c7-840a-1feb166e3bb4",
                    "240aec0a-533b-4806-90ea-7ea57b39d85c",
                    "83b9943e-17d5-4174-b45c-8da090924b0f",
                    "af992ff6-9053-499c-a622-82bf25195a73",
                    "2db55ce9-4df7-42fc-bfc9-40eb87d1fcf2",
                    "c08e03c8-5e85-4702-972e-a99c142a5d0c",
                    "593be794-f102-4207-903c-9eec35ade9bd",
                    "13213328-08cc-47fd-9f3e-0df23934018d",
                    "9b674557-7ef6-430a-b343-8541999571b2",
                    "ef3e09ca-a800-4e82-9502-805e4234e00b",
                    "07798279-d8fc-4dab-942e-db0c465f9a6d",
                    "ba9ce296-11c9-45f5-92ad-c5b757abeb0b",
                    "401cffa4-7c4e-4b2b-b2a6-3e706dec6e30",
                    "5149a055-5923-41c8-a2a4-413652c0acda",
                    "f3b409b1-82ea-4e5b-8fd3-783f0b6bfebf",
                    "6b090de9-add1-4206-b2e8-8247b78465a0",
                    "f4aa40e5-7373-41e4-bd61-f954adecf87c",
                    "d5ca2cb2-b93b-4b3b-8310-106027e8b518",
                    "f27e02f3-8d20-4fb0-8567-55c6b05601e0",
                    "1673caca-45bb-4940-8f05-049e3c812eb1",
                    "37e93cc3-33d6-4530-9ed5-6bb84f03513c",
                    "00bf778d-38c6-4610-8849-8f30d9b50dc5",
                    "5a41e71e-3039-44a5-8b09-732214f4297d",
                    "7c30ead4-a716-4a97-90ea-a89256575072",
                    "039997a1-b9b6-42eb-9282-e01e2f4d8d1a",
                    "677c003a-6ad7-4981-a1b1-bf51c9e4fd25",
                    "3df849a6-b731-4b03-b98d-9c5875932949",
                    "7a0c8e41-0b41-4266-99a1-6f14fc2aef9a",
                    "43fb48d4-79a3-4c89-b6a7-cfbf0d60c09b",
                    "a0eb6011-f2bc-47b2-a42e-63c92f390374",
                    "500f5d7e-a53a-4537-8d7c-75cbe8d5f34f",
                    "c898b910-ca95-4e51-bc9e-b3ad2b01f6e6",
                    "9d313eaa-8516-474c-b61e-0d342e3056e5",
                    "6c5500b9-5a91-4818-8a63-3ecc92f8222a",
                    "1076d54e-3d83-4259-96aa-86126ff60a0d",
                    "312d2a2f-164b-4563-9243-406e06468e8d",
                    "72e010e5-4f35-4aa2-8fbc-b893f63439b0",
                    "2b239b7a-e4d7-4163-ab1c-7dfe4b3feda1",
                    "c187b2bb-0489-4915-bc73-0d7697b92cde",
                    "1c7aa124-cabc-40ff-8a3a-152862a95fe3",
                    "16175b2b-2fc3-4566-aa25-40cffc78a0d2",
                    "2b09a23e-6b6f-4296-b506-15a9a7fd836b",
                    "43080e9c-84c4-4d30-a8a8-ebe3694d84af",
                    "c546f2c3-856a-4ca0-9736-3430fd5c272a",
                    "ecdb54a9-c58f-48af-9141-766840bf6d44",
                    "1da6f0cc-7377-43fa-954a-bac4c0652679",
                    "927814c2-ef08-428a-97cd-7b20ee789933",
                    "83a47fa4-7181-4fe1-95a3-cb7581083484",
                    "7513629d-fc54-4615-854e-d0e79695c589",
                    "b2c8246d-1860-461f-a08e-a68bb4be8614",
                    "a77fa62a-1c6d-4c97-9e91-c2ad658488f8",
                    "1b6bf94c-8c03-4fa1-bbfc-d2edd2606e44",
                    "379c9382-b864-4a14-b8cd-7d02f0bd1a41",
                    "4c978c6c-ba9a-40d1-a974-28abbeb275c1",
                    "aa8bcabe-b5cf-4fe3-87a8-fdabe751bfe2",
                    "f7d9b112-4871-4cd3-8d13-9a68a637a88e",
                    "fe6a9cb9-cb21-4885-8cf1-c06efc2c9195",
                    "3a70a93a-3c10-4a2d-8359-26d895e15634",
                    "9df523ef-9cff-4edd-b765-2a5c827b5f07",
                    "f88a9e57-ec84-48eb-8460-4bf0d7800d41",
                    "0616bebe-41b8-4df5-b4ff-2b5de87cff50",
                    "550bad5c-54af-4c90-842e-286b87ef5a24",
                    "00935609-3a08-4ce4-a5da-ed62d6e943a1",
                    "e353efa6-7140-43e0-9453-e31cde16cb83",
                    "548c91f4-25da-4ee7-a3c3-cd507e181af0",
                    "c53a2e2f-102d-4cc5-a6b8-4c9ad07c18c7",
                    "b735290f-bbeb-4ff4-8e37-ade1e788389b",
                    "c62970d0-097e-484c-9780-8c1c6c77d8b8",
                    "b370022a-6ac8-4cf7-8460-526e3e899f06",
                    "48b9c990-ccbf-467b-9015-babb377110a2",
                    "7c5875be-38c1-453d-a196-1a7f1df1585a",
                    "187b444b-c088-48cf-8ad8-a5f7a6a8d44a",
                    "8a5bf161-29d2-4b4e-bab9-3695251a03e6",
                    "b2575f15-f3d5-43a2-b8b4-eef5590ffdad",
                    "b9e6c44a-d5ff-4b32-828f-50aa2bf4f237",
                    "297de13a-d375-471f-9ead-ec4f4aca8e1e",
                    "a860a7ab-cec4-4889-afed-676c017dfb31",
                    "a17e6f68-6992-4ec8-9f09-1b30a8d824da",
                    "69013524-5214-4715-b916-9eac2010e1ac",
                    "96b3e2a3-d251-47eb-a82d-9a35ad9290da",
                    "943684ed-ed97-4e95-a391-7c16d6d293ab",
                    "5d6e8a8a-5778-4519-af4d-68b4ab99847d",
                    "66320b37-b8b4-4eb2-996e-75daa57cf586",
                    "f1033abe-6bce-45ee-a5b7-1db25dd411e9",
                    "a14b5f06-b08b-4b40-98b7-3e22d69bfca3",
                    "fe245917-0950-4cbe-9032-ba7babe2a4b4",
                    "ee662548-8eff-4abe-a80d-d22f2e9d14e7",
                    "caea2f10-aab2-46e3-83a2-a7513ddffee0",
                    "0208d70f-bd62-45fe-90ac-fd5ed6cc2815",
                    "f8eedfeb-656e-4873-9fbf-595723f4bd5c",
                    "7b46e4d4-affe-4672-bb43-2ea1b3e50ca2",
                    "785e28cd-a127-4629-bdba-8dd713e4f2b7",
                    "ff22c7b4-c07a-499b-a1ce-bcf9081b2c1c",
                    "de2a554a-cd9d-4e11-9bcc-00b6a9fd349b",
                    "868aa095-4850-414c-8682-d07705f8856c",
                    "456cfdfd-8b35-4964-8715-2a18be48ca9f",
                    "f2417566-19a1-414a-9095-1faa0af2129d",
                    "410e17ca-a950-4d4a-ba42-ec305d829ed7",
                    "810eda74-17b9-4bec-b4c9-d490483b8935",
                    "a79a202f-be97-4b35-bd61-f0b3534af32c",
                    "0f86ae20-6b36-43ec-9a83-2c25d89ef975",
                    "093d1773-0fc5-4945-b247-21705ba84faa",
                    "82516aac-cd2e-435a-be5d-f552f770989d",
                    "a480a72c-4599-41b9-80d5-ba9ce73e433b",
                    "180cfeb3-15eb-4887-9314-04ac58b870f8",
                    "ba4d9b1b-1e1b-4b8a-8931-eb15a3260af0",
                    "6a41f338-fc22-416c-80d5-ef283d60dd20",
                    "4e168136-231e-44d0-83ca-2ab12a619459",
                    "2a2ba645-0944-47ad-a4b8-1fde9ab2bedc",
                    "3a1c7746-e5c9-44ca-ad73-4763f1c9fb44",
                    "e3977980-0bbf-4f9c-ad00-a36eaafb4ef8",
                    "d70dc09a-d452-4fa4-b590-1304a0b907d7",
                    "f69673f0-920b-4679-b387-2ddb119f9222",
                    "593b4bba-a018-4c43-bd88-04a75eb717d0",
                    "c5bc9c05-3178-4f11-a75a-81c8e41ee7f4",
                    "9ad10630-bd09-4874-8bfa-ea571d2d9670",
                )
                rebuilder.forEach { hendelse ->
                    if (hendelse is HendelseModel.EksterntVarselFeilet && hendelse.notifikasjonId.toString() in updateNeeded) {
                        eksternVarslingRepository.oppdaterModellEtterHendelse(hendelse)
                    }
                }
            }


            launch {
                hendelsestrøm.forEach { event ->
                    eksternVarslingRepository.oppdaterModellEtterHendelse(event)
                }
            }

            launch {
                val service = EksternVarslingService(
                    eksternVarslingRepository = eksternVarslingRepository,
                    altinn2VarselKlient = basedOnEnv(
                        prod = { Altinn2VarselKlientImpl() },
                        dev = {
                            Altinn2VarselKlientMedFilter(
                                eksternVarslingRepository,
                                Altinn2VarselKlientImpl(),
                                Altinn2VarselKlientLogging()
                            )
                        },
                        other = { Altinn2VarselKlientLogging() },
                    ),
                    altinn3VarselKlient = basedOnEnv(
                        prod = { Altinn3VarselKlientImpl() },
                        dev = {
                            Altinn3VarselKlientMedFilter(
                                eksternVarslingRepository,
                                Altinn3VarselKlientLogging()
                            )
                        },
                        other = { Altinn3VarselKlientLogging() },
                    ),
                    hendelseProdusent = hendelseProdusent,
                )
                service.start(this)
            }

            launch {
                val service = EksternVarslingStatusEksportService(
                    eventSource = exporterHendelsestrøm,
                    repo = eksternVarslingRepository,
                )
                service.start(this)
            }

            configureRouting {
                val internalTestClient = basedOnEnv(
                    prod = { Altinn2VarselKlientImpl() },
                    dev = { Altinn2VarselKlientImpl() },
                    other = { Altinn2VarselKlientLogging() }
                )
                get("/internal/send_sms") {
                    testSms(internalTestClient)
                }
                get("/internal/send_epost") {
                    testEpost(internalTestClient)
                }
                post("/internal/update_emergency_brake") {
                    updateEmergencyBrake(eksternVarslingRepository)
                }
            }
            registerShutdownListener()
        }.start(wait = true)
    }
}

data class TestSmsRequestBody(
    val reporteeNumber: String,
    val tlf: String,
    val tekst: String,
)

suspend fun RoutingContext.testSms(altinnVarselKlient: Altinn2VarselKlient) {
    val varselRequest = call.receive<TestSmsRequestBody>()
    testSend(altinnVarselKlient) {
        sendSms(
            mobilnummer = varselRequest.tlf,
            reporteeNumber = varselRequest.reporteeNumber,
            tekst = varselRequest.tekst,
        )
    }
}

data class TestEpostRequestBody(
    val reporteeNumber: String,
    val epost: String,
    val subject: String,
    val body: String,
)

suspend fun RoutingContext.testEpost(altinnVarselKlient: Altinn2VarselKlient) {
    val varselRequest = call.receive<TestEpostRequestBody>()
    this.testSend(altinnVarselKlient) {
        sendEpost(
            reporteeNumber = varselRequest.reporteeNumber,
            epostadresse = varselRequest.epost,
            tittel = varselRequest.subject,
            tekst = varselRequest.body,
        )
    }
}

private suspend fun RoutingContext.testSend(
    client: Altinn2VarselKlient,
    action: suspend Altinn2VarselKlientImpl.() -> AltinnVarselKlientResponseOrException
) {
    if (client is Altinn2VarselKlientImpl) {
        when (val response = client.action()) {
            is AltinnVarselKlientResponse.Ok ->
                call.respond(HttpStatusCode.OK, laxObjectMapper.writeValueAsString(response.rå))

            is AltinnVarselKlientResponse.Feil ->
                call.respond(HttpStatusCode.BadRequest, laxObjectMapper.writeValueAsString(response.rå))

            is UkjentException ->
                call.respond(
                    HttpStatusCode.InternalServerError,
                    laxObjectMapper.writeValueAsString(
                        mapOf(
                            "type" to response.exception.javaClass.canonicalName,
                            "msg" to response.exception.message,
                        )
                    )
                )
        }
    }
}

data class UpdateEmergencyBrakeRequestBody(
    val newState: Boolean
)

suspend fun RoutingContext.updateEmergencyBrake(
    eksternVarslingRepository: EksternVarslingRepository
) {
    val newState = call.receive<UpdateEmergencyBrakeRequestBody>().newState
    eksternVarslingRepository.updateEmergencyBrakeTo(newState)
    call.respond(HttpStatusCode.OK)
}