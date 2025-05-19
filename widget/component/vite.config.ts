import { defineConfig, ViteDevServer } from 'vite'
import { extname, relative, resolve } from 'path'
import { fileURLToPath } from 'node:url'
import { glob } from 'glob'
import react from '@vitejs/plugin-react'
import dts from 'vite-plugin-dts'
import { libInjectCss } from 'vite-plugin-lib-inject-css'
import express from 'express'
import { applyNotifikasjonMockMiddleware } from './mock/dist/notifikasjonMockMiddleware.js'

export default defineConfig({
  plugins: [
    react(),
    libInjectCss(),
    dts({ include: ['lib'] }),
    {
      name: 'vite-plugin-mock-graphql',
      configureServer(server: ViteDevServer) {
        const app = express()
        applyNotifikasjonMockMiddleware({ app, path: '/api/graphql' })
        server.middlewares.use(app)
      }
    }
  ],
  build: {
    copyPublicDir: false,
    lib: {
      entry: resolve(__dirname, 'lib/main.ts'),
      formats: ['es']
    },
    rollupOptions: {
      external: ['react', 'react/jsx-runtime'],
      input: Object.fromEntries(
          glob.sync('lib/**/*.{ts,tsx}', {
            ignore: ['lib/**/*.d.ts'],
          }).map(file => [
            relative('lib', file.slice(0, file.length - extname(file).length)),
            fileURLToPath(new URL(file, import.meta.url))
          ])
      ),
      output: {
        assetFileNames: 'assets/[name][extname]',
        entryFileNames: '[name].js',
      }
    }
  }
})
