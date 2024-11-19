import path from 'path';
import express from 'express';
import httpProxyMiddleware, {
    debugProxyErrorsPlugin,
    errorResponsePlugin,
    proxyEventsPlugin,
} from 'http-proxy-middleware';
import {createLogger, format, transports} from 'winston';
import {fetchAccessToken} from "./accessToken.js";
import fs from "fs";
import {ApolloServer, gql} from 'apollo-server-express';

const {createProxyMiddleware} = httpProxyMiddleware;

const {
    PORT = 8080,
    GIT_COMMIT = '?',
    NAIS_CLUSTER_NAME = 'local',
} = process.env;

const maskFormat = format((info) => ({
    ...info,
    message: info.message.replace(/\d{9,}/g, (match) => '*'.repeat(match.length)),
}));

const log = createLogger({
    format: maskFormat(),
    transports: [
        new transports.Console({
            timestamp: true,
            format: format.combine(format.splat(), format.json()),
        }),
    ],
});

// copy with mods from http-proxy-middleware https://github.com/chimurai/http-proxy-middleware/blob/master/src/plugins/default/logger-plugin.ts
const loggerPlugin = (proxyServer, options) => {
    proxyServer.on('error', (err, req, res, target) => {
        const hostname = req?.headers?.host;
        // target is undefined when websocket errors
        const errReference = 'https://nodejs.org/api/errors.html#errors_common_system_errors'; // link to Node Common Systems Errors page
        const level =
            /HPE_INVALID/.test(err.code) ||
            ['ECONNRESET', 'ENOTFOUND', 'ECONNREFUSED', 'ETIMEDOUT'].includes(err.code)
                ? 'warn'
                : 'error';
        log.log(
            level,
            '[HPM] Error occurred while proxying request %s to %s [%s] (%s)',
            `${hostname}${req?.host}${req?.path}`,
            `${target?.href}`,
            err.code || err,
            errReference
        );
    });

    proxyServer.on('proxyRes', (proxyRes, req, res) => {
        const originalUrl = req.originalUrl ?? `${req.baseUrl || ''}${req.url}`;
        const pathUpToSearch = proxyRes.req.path.replace(/\?.*$/, '');
        const exchange = `[HPM] ${req.method} ${originalUrl} -> ${proxyRes.req.protocol}//${proxyRes.req.host}${pathUpToSearch} [${proxyRes.statusCode}]`;
        log.info(exchange);
    });

    /**
     * When client opens WebSocket connection
     */
    proxyServer.on('open', (socket) => {
        log.info('[HPM] Client connected: %o', socket.address());
    });

    /**
     * When client closes WebSocket connection
     */
    proxyServer.on('close', (req, proxySocket, proxyHead) => {
        log.info('[HPM] Client disconnected: %o', proxySocket.address());
    });
};

log.info(`Frackend startup: ${JSON.stringify({NAIS_CLUSTER_NAME, GIT_COMMIT})}`);

let BUILD_PATH = path.join(process.cwd(), '../build');
const GRAPHQL_ENDPOINT = 'http://notifikasjon-produsent-api/api/graphql'

const main = async () => {
    const app = express();
    app.disable('x-powered-by');

    const proxyOptions = {
        logger: log,
        secure: true,
        xfwd: true,
        changeOrigin: true,
        ejectPlugins: true,
        plugins: [
            debugProxyErrorsPlugin,
            errorResponsePlugin,
            loggerPlugin,
            proxyEventsPlugin,
        ],
    };

    if (NAIS_CLUSTER_NAME === 'local') {
        const sdl = fs.readFileSync('../../app/src/main/resources/produsent.graphql');
        const typeDefs = gql(sdl.toString());
        const server = new ApolloServer({
            typeDefs,
            mocks: {
                ISO8601DateTime: () => new Date().toISOString(),
            },
        });
        await server.start();
        log.info("🤖🤖 Started local mock ApolloServer, all responses are fake 🤖🤖");
        server.applyMiddleware({app, path: '/notifikasjon-produsent-api'});
    } else {
        app.use(
            '/notifikasjon-produsent-api',
            async (req, res, next) => {
                try {
                    const accessToken = await fetchAccessToken();
                    req.headers.authorization = `Bearer ${accessToken}`;
                    next();
                } catch (err) {
                    next(err);
                }
            },
            createProxyMiddleware({
                ...proxyOptions,
                pathRewrite: {'^/': ''},
                target: GRAPHQL_ENDPOINT,
            })
        );
    }

    app.get('/ok', (req, res) => res.sendStatus(200));
    app.use('/', express.static(BUILD_PATH, { maxAge: '1h' }));

    app.listen(PORT, () => {
        log.info(`Server listening on port ${PORT}`);
    });
};

main()
    .then((_) => log.info('main started'))
    .catch((e) => log.error('main failed', e));
