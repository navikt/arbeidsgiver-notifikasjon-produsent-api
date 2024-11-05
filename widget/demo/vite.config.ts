import {defineConfig, ViteDevServer} from 'vite'
import express from 'express'
import react from '@vitejs/plugin-react-swc'
import {applyNotifikasjonMockMiddleware} from '@navikt/arbeidsgiver-notifikasjoner-brukerapi-mock'

const app = express()
applyNotifikasjonMockMiddleware({app, path: "/api/graphql"});

// https://vitejs.dev/config/
export default defineConfig({
  base: '/',
  plugins: [
    react(),
    {
      name: 'express-plugin',
      config() {
        return {
          server: { proxy: { '/api/graphql': 'http://localhost:3000' } },
          preview: { proxy: { '/api/graphql': 'http://localhost:3000' } },
        }
      },
      configureServer(server: ViteDevServer) {
        server.middlewares.use(app)
      }
    }
  ]
})
