import { defineConfig, type Plugin, type ViteDevServer } from 'vite';
import { extname, relative, resolve } from 'path';
import { fileURLToPath } from 'node:url';
import { glob } from 'glob';
import react from '@vitejs/plugin-react';
import dts from 'vite-plugin-dts';
import { libInjectCss } from 'vite-plugin-lib-inject-css';

const config = async ({ command }) => {
  const isDev = command === 'serve';

  const devMockPlugin: Plugin | undefined = isDev
    ? {
      name: 'vite-plugin-mock-graphql',
      configureServer: async (server: ViteDevServer) => {
        try {
          console.log('Adding /api/graphql mock server');
          const express = (await import('express')).default;
          const { applyNotifikasjonMockMiddleware } = await import('./mock/dist/notifikasjonMockMiddleware.js');
          const app = express();
          app.use(express.json());
          await applyNotifikasjonMockMiddleware(app, {}, '/api/graphql');
          server.middlewares.use(app);
        } catch (err) {
          console.warn('Mock middleware not available, skipping setup.');
        }
      },
    }
    : undefined;

  return defineConfig({
    plugins: [
      react(),
      libInjectCss(),
      dts({ include: ['lib'] }),
      ...(devMockPlugin ? [devMockPlugin] : []),
    ],
    build: {
      copyPublicDir: false,
      lib: {
        entry: resolve(__dirname, 'lib/main.ts'),
        formats: ['es'],
      },
      rollupOptions: {
        external: [
          'react',
          'react/jsx-runtime',
          'react-dom',
          'react-dom/client',
        ],
        input: Object.fromEntries(
          glob.sync('lib/**/*.{ts,tsx}', {
            ignore: ['lib/**/*.d.ts'],
          }).map(file => [
            relative('lib', file.slice(0, file.length - extname(file).length)),
            fileURLToPath(new URL(file, import.meta.url)),
          ]),
        ),
        output: {
          assetFileNames: 'assets/[name][extname]',
          entryFileNames: '[name].js',
        },
      },
    },
  });
};

export default config;
