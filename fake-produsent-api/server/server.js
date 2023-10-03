import fs from 'fs'
import express from 'express';
import casual from 'casual';
import {createLogger, transports, format} from 'winston';
import require from "./esm-require.js";

const {PORT = 8080} = process.env;

const { ApolloServerPluginLandingPageGraphQLPlayground } = require("apollo-server-core");
const {ApolloServer, gql} = require('apollo-server-express');

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
            log.info(`🚀 Server ready at :${PORT}${server.graphqlPath}`);
        });
    } catch (error) {
        log.error(`Server failed to start ${error}`);
        process.exit(1);
    }

}

serve().then(/*noop*/);