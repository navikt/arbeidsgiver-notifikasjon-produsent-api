package no.nav.arbeidsgiver.notifikasjon.infrastruktur.altinn

import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Database

interface AltinnRolleRepository {
    suspend fun leggTilAltinnRoller(roller: Iterable<AltinnRolle>)
    suspend fun hentAltinnrolle(rolleKode: String): AltinnRolle?
    suspend fun hentAlleAltinnRoller(): List<AltinnRolle>
}

open class AltinnRolleRepositoryImpl(
    private val database: Database,
) : AltinnRolleRepository {
    override suspend fun leggTilAltinnRoller(roller: Iterable<AltinnRolle>) {
        database.nonTransactionalExecuteBatch(
            """
            insert into altinn_rolle(
                 role_definition_id,
                 role_definition_code                 
            )
            values (?, ?)
            """,
            roller
        ) {
            string(it.RoleDefinitionId)
            string(it.RoleDefinitionCode)
        }
    }

    override suspend fun hentAltinnrolle(rolleKode: String): AltinnRolle? =
        database.nonTransactionalExecuteQuery("""
                select role_definition_id,
                    role_definition_code                
                from altinn_rolle                
                where role_definition_code = ?
        """,
            { string(rolleKode) }
        ) {
            AltinnRolle(
                RoleDefinitionCode = getString("role_definition_code"),
                RoleDefinitionId = getString("role_definition_id")
            )
        }.firstOrNull()

    override suspend fun hentAlleAltinnRoller(): List<AltinnRolle> =
        database.nonTransactionalExecuteQuery(
            """
                select role_definition_id,
                    role_definition_code                
                from altinn_rolle                          
        """
        ) {
            AltinnRolle(
                RoleDefinitionCode = getString("role_definition_code"),
                RoleDefinitionId = getString("role_definition_id")
            )
        }
}