import fs from 'fs'
import express from 'express';
import casual from 'casual';
import {createLogger, transports, format} from 'winston';
import {ApolloServerPluginLandingPageGraphQLPlayground} from "apollo-server-core";
import {ApolloServer, gql} from 'apollo-server-express';

const {
    PORT = 8080,
    ALWAYS_SUCCESSFUL_RESPONSE = 'false',
} = process.env;


const successfulMocks = {
    HardDeleteNotifikasjonResultat: () => ({__typename: "HardDeleteNotifikasjonVellykket"}),
    HardDeleteSakResultat: () => ({__typename: "HardDeleteSakVellykket"}),
    HentNotifikasjonResultat: () => ({__typename: "HentetNotifikasjon"}),
    HentSakResultat: () => ({__typename: "HentetSak"}),
    MineNotifikasjonerResultat: () => ({__typename: "NotifikasjonConnection"}),
    NyBeskjedResultat: () => ({__typename: "NyBeskjedVellykket"}),
    NyKalenderavtaleResultat: () => ({__typename: "NyKalenderavtaleVellykket"}),
    NyOppgaveResultat: () => ({__typename: "NyOppgaveVellykket"}),
    NySakResultat: () => ({__typename: "NySakVellykket"}),
    NyStatusSakResultat: () => ({__typename: "NyStatusSakVellykket"}),
    OppdaterKalenderavtaleResultat: () => ({__typename: "OppdaterKalenderavtaleVellykket"}),
    OppgaveUtfoertResultat: () => ({__typename: "OppgaveUtfoertVellykket"}),
    OppgaveUtgaattResultat: () => ({__typename: "OppgaveUtgaattVellykket"}),
    OppgaveUtsettFristResultat: () => ({__typename: "OppgaveUtsettFristVellykket"}),
    SoftDeleteNotifikasjonResultat: () => ({__typename: "SoftDeleteNotifikasjonVellykket" }),
    SoftDeleteSakResultat: () => ({__typename: "SoftDeleteSakVellykket"}),
    TilleggsinformasjonSakResultat: () => ({__typename: "TilleggsinformasjonSakVellykket"}),
};

const log = createLogger({
    transports: [
        new transports.Console({
            timestamp: true,
            format: format.json()
        })
    ]
})

const serve = async () => {
    try {
        const app = express();
        app.disable("x-powered-by");
        app.get('/isalive', (req, res) => res.sendStatus(200));
        app.get('/isready', (req, res) => res.sendStatus(200));
        app.get('/started', (req, res) => res.sendStatus(200));

        const sdl = fs.readFileSync('./produsent.graphql');
        const typeDefs = gql(sdl.toString());
        const server = new ApolloServer({
            typeDefs,
            mocks: {
                Int: () => casual.integer(0, 1000),
                String: () => casual.string,
                ISO8601DateTime: () => new Date().toISOString(),
                ...(ALWAYS_SUCCESSFUL_RESPONSE === 'true' ? successfulMocks : {})
            },
            plugins: [
                ApolloServerPluginLandingPageGraphQLPlayground(),
            ],
            playground: {
                endpoint: '/',
                settings: {
                    "editor.theme": "dark"
                }
            }
        });
        await server.start();
        server.applyMiddleware({app, path: '/'});
        app.listen(PORT, () => {
            log.info(`🚀 Server ready at :${PORT}${server.graphqlPath} ${ALWAYS_SUCCESSFUL_RESPONSE === 'true' ? 'with always successful mocks' : ''}`);
        });
    } catch (error) {
        log.error(`Server failed to start ${error}`);
        process.exit(1);
    }

}

serve().then(/*noop*/);
