import * as webpack from 'webpack'
// @ts-ignore
import nodeExternals = require('webpack-node-externals');

// TODO: ikke generer d.ts-filer for interne typer i ./dist/ (så
// de ikke dukker opp i den publiserte npm-pakken.

const config: webpack.Configuration = {
  mode: 'production',
  entry: './src/index.tsx',
  output: {
    filename: 'index.js',
    libraryTarget: 'commonjs2'
  },
  module: {
    rules: [
      {
        test: /\.ts(x?)$/,
        use: [
          {
            loader: 'babel-loader'
          }
        ]
      },
      {
        test: /\.module\.css$/,
        use: [
          'style-loader',
          {
            loader: 'css-loader',
            options: {
              importLoaders: 1,
              modules: true
            }
          }
        ]
      },
      {
        test: /\.svg$/,
        use: ['@svgr/webpack']
      }
    ]
  },
  resolve: {
    extensions: ['.ts', '.tsx', '.module.css', '.svg']
  },
  externals: [nodeExternals()],
}

export default config
