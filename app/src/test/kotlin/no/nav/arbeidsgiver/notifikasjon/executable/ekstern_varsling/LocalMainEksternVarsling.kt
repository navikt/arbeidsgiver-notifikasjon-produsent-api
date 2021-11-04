package no.nav.arbeidsgiver.notifikasjon.executable.ekstern_varsling

import com.fasterxml.jackson.databind.node.NullNode
import db.migration.OS
import no.nav.arbeidsgiver.notifikasjon.EksternVarsling
import no.nav.arbeidsgiver.notifikasjon.ekstern_varsling.AltinnVarselKlient
import no.nav.arbeidsgiver.notifikasjon.ekstern_varsling.EksternVarsel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger


fun main(@Suppress("UNUSED_PARAMETER") args: Array<String>) {
    OS.setupLocal()
    EksternVarsling.main(
        httpPort = 8085,
        altinnVarselKlient = object: AltinnVarselKlient {
            val log = logger()
            override suspend fun send(eksternVarsel: EksternVarsel): Result<AltinnVarselKlient.AltinnResponse> {
                log.info("stub altinn varsel klient, send: $eksternVarsel")
                return Result.success(AltinnVarselKlient.AltinnResponse.Ok(NullNode.instance))
            }
        }
    )
}

