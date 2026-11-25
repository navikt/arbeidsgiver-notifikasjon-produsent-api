import fs from 'fs'
import express from 'express';
import cors from 'cors';
import casual from 'casual';
import {buildSchema} from 'graphql';
import {createLogger, transports, format} from 'winston';
import {ApolloServer} from '@apollo/server';
import {ApolloServerPluginLandingPageLocalDefault} from '@apollo/server/plugin/landingPage/default';
import {expressMiddleware} from '@as-integrations/express4';
import {addMocksToSchema} from '@graphql-tools/mock';

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
        const schema = addMocksToSchema({
            schema: buildSchema(sdl.toString()),
            mocks: {
                Int: () => casual.integer(0, 1000),
                String: () => casual.string,
                ISO8601DateTime: () => new Date().toISOString(),
                ...(ALWAYS_SUCCESSFUL_RESPONSE === 'true' ? successfulMocks : {})
            },
        });
        const server = new ApolloServer({
            schema,
            introspection: true,
            plugins: [
                // NODE_ENV=production would otherwise hide the interactive IDE
                ApolloServerPluginLandingPageLocalDefault({embed: true}),
            ],
        });
        await server.start();
        app.use('/', cors(), express.json(), expressMiddleware(server));
        app.listen(PORT, () => {
            log.info(`🚀 Server ready at :${PORT}/ ${ALWAYS_SUCCESSFUL_RESPONSE === 'true' ? 'with always successful mocks' : ''}`);
        });
    } catch (error) {
        log.error(`Server failed to start ${error}`);
        process.exit(1);
    }

}

serve().then(/*noop*/);
