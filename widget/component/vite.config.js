var __awaiter = (this && this.__awaiter) || function (thisArg, _arguments, P, generator) {
    function adopt(value) { return value instanceof P ? value : new P(function (resolve) { resolve(value); }); }
    return new (P || (P = Promise))(function (resolve, reject) {
        function fulfilled(value) { try { step(generator.next(value)); } catch (e) { reject(e); } }
        function rejected(value) { try { step(generator["throw"](value)); } catch (e) { reject(e); } }
        function step(result) { result.done ? resolve(result.value) : adopt(result.value).then(fulfilled, rejected); }
        step((generator = generator.apply(thisArg, _arguments || [])).next());
    });
};
var __generator = (this && this.__generator) || function (thisArg, body) {
    var _ = { label: 0, sent: function() { if (t[0] & 1) throw t[1]; return t[1]; }, trys: [], ops: [] }, f, y, t, g = Object.create((typeof Iterator === "function" ? Iterator : Object).prototype);
    return g.next = verb(0), g["throw"] = verb(1), g["return"] = verb(2), typeof Symbol === "function" && (g[Symbol.iterator] = function() { return this; }), g;
    function verb(n) { return function (v) { return step([n, v]); }; }
    function step(op) {
        if (f) throw new TypeError("Generator is already executing.");
        while (g && (g = 0, op[0] && (_ = 0)), _) try {
            if (f = 1, y && (t = op[0] & 2 ? y["return"] : op[0] ? y["throw"] || ((t = y["return"]) && t.call(y), 0) : y.next) && !(t = t.call(y, op[1])).done) return t;
            if (y = 0, t) op = [op[0] & 2, t.value];
            switch (op[0]) {
                case 0: case 1: t = op; break;
                case 4: _.label++; return { value: op[1], done: false };
                case 5: _.label++; y = op[1]; op = [0]; continue;
                case 7: op = _.ops.pop(); _.trys.pop(); continue;
                default:
                    if (!(t = _.trys, t = t.length > 0 && t[t.length - 1]) && (op[0] === 6 || op[0] === 2)) { _ = 0; continue; }
                    if (op[0] === 3 && (!t || (op[1] > t[0] && op[1] < t[3]))) { _.label = op[1]; break; }
                    if (op[0] === 6 && _.label < t[1]) { _.label = t[1]; t = op; break; }
                    if (t && _.label < t[2]) { _.label = t[2]; _.ops.push(op); break; }
                    if (t[2]) _.ops.pop();
                    _.trys.pop(); continue;
            }
            op = body.call(thisArg, _);
        } catch (e) { op = [6, e]; y = 0; } finally { f = t = 0; }
        if (op[0] & 5) throw op[1]; return { value: op[0] ? op[1] : void 0, done: true };
    }
};
var __spreadArray = (this && this.__spreadArray) || function (to, from, pack) {
    if (pack || arguments.length === 2) for (var i = 0, l = from.length, ar; i < l; i++) {
        if (ar || !(i in from)) {
            if (!ar) ar = Array.prototype.slice.call(from, 0, i);
            ar[i] = from[i];
        }
    }
    return to.concat(ar || Array.prototype.slice.call(from));
};
import { defineConfig } from 'vite';
import { extname, relative, resolve } from 'path';
import { fileURLToPath } from 'node:url';
import { glob } from 'glob';
import react from '@vitejs/plugin-react';
import dts from 'vite-plugin-dts';
import { libInjectCss } from 'vite-plugin-lib-inject-css';
var config = function (_a) { return __awaiter(void 0, [_a], void 0, function (_b) {
    var isDev, devMockPlugin;
    var command = _b.command;
    return __generator(this, function (_c) {
        isDev = command === 'serve';
        devMockPlugin = isDev
            ? {
                name: 'vite-plugin-mock-graphql',
                configureServer: function (server) { return __awaiter(void 0, void 0, void 0, function () {
                    var express, applyNotifikasjonMockMiddleware, app, err_1;
                    return __generator(this, function (_a) {
                        switch (_a.label) {
                            case 0:
                                _a.trys.push([0, 3, , 4]);
                                return [4 /*yield*/, import('express')];
                            case 1:
                                express = (_a.sent()).default;
                                return [4 /*yield*/, import('./mock/dist/notifikasjonMockMiddleware.js')];
                            case 2:
                                applyNotifikasjonMockMiddleware = (_a.sent()).applyNotifikasjonMockMiddleware;
                                app = express();
                                applyNotifikasjonMockMiddleware({ app: app, path: '/api/graphql' });
                                server.middlewares.use(app);
                                return [3 /*break*/, 4];
                            case 3:
                                err_1 = _a.sent();
                                console.warn('Mock middleware not available, skipping setup.');
                                return [3 /*break*/, 4];
                            case 4: return [2 /*return*/];
                        }
                    });
                }); }
            }
            : undefined;
        return [2 /*return*/, defineConfig({
                plugins: __spreadArray([
                    react(),
                    libInjectCss(),
                    dts({ include: ['lib'] })
                ], (devMockPlugin ? [devMockPlugin] : []), true),
                server: {
                    watch: {
                        ignored: ['!**/lib/**'] // force Vite to watch lib
                    }
                },
                build: {
                    copyPublicDir: false,
                    lib: {
                        entry: resolve(__dirname, 'lib/main.ts'),
                        formats: ['es']
                    },
                    rollupOptions: {
                        external: ['react', 'react/jsx-runtime'],
                        input: Object.fromEntries(glob.sync('lib/**/*.{ts,tsx}', {
                            ignore: ['lib/**/*.d.ts'],
                        }).map(function (file) { return [
                            relative('lib', file.slice(0, file.length - extname(file).length)),
                            fileURLToPath(new URL(file, import.meta.url))
                        ]; })),
                        output: {
                            assetFileNames: 'assets/[name][extname]',
                            entryFileNames: '[name].js',
                        }
                    }
                }
            })];
    });
}); };
export default config;
