const esbuild = require('esbuild');
const postcssPlugin = require('esbuild-style-plugin');
const { exec } = require('child_process');
const fs = require('fs');

const pkg = JSON.parse(fs.readFileSync('./package.json', 'utf8'));

const external = [
  ...Object.keys(pkg.dependencies || {}),
  ...Object.keys(pkg.peerDependencies || {}),
];

const buildOptions = (format, outdir) => ({
  entryPoints: ['src/index.tsx'],
  outdir,
  bundle: true,
  sourcemap: true,
  format,
  target: ['esnext'],
  external,
  minify: false,
  plugins: [
    postcssPlugin({
      postcssOptions: {
        plugins: [],
      },
      inject: false,
      extract: true,
    }),
  ],
});

const watchMode = process.argv.includes('--watch');

async function buildAll() {
  // Step 1: Generate .d.ts using tsc
  const tscCmd = 'tsc --declaration --emitDeclarationOnly --outDir lib/esm';
  console.log('📦 Generating type declarations...');
  await new Promise((resolve, reject) => {
    exec(tscCmd, (error, stdout, stderr) => {
      if (stdout) console.log(stdout);
      if (stderr) console.error(stderr);
      if (error) reject(error);
      else resolve();
    });
  });

  console.log(`📦 Building ${watchMode ? 'in watch mode' : 'once'}...`);

  const contextESM = await esbuild.context(buildOptions('esm', 'lib/esm'));
  const contextCJS = await esbuild.context(buildOptions('cjs', 'lib/cjs'));

  if (watchMode) {
    await contextESM.watch();
    await contextCJS.watch();
    console.log('👀 Watching for changes...');
  } else {
    await contextESM.rebuild();
    await contextCJS.rebuild();
    console.log('✅ Build complete');
    await contextESM.dispose();
    await contextCJS.dispose();
  }
}

buildAll().catch((err) => {
  console.error('❌ Build failed:', err);
  process.exit(1);
});
