const path = require('path');
const fs = require('fs');
const webpack = require('webpack');
const HtmlWebpackPlugin = require('html-webpack-plugin');

const loadDevCerts = () => {
  try {
    return {
      key: fs.readFileSync('./certificates/localhost-key.pem'),
      cert: fs.readFileSync('./certificates/localhost.pem'),
    };
  } catch {
    return false;
  }
};

module.exports = (env, argv) => {
  const isDev = argv.mode === 'development';

  return {
    entry: './src/index.tsx',
    mode: 'development',
    output: {
      path: path.resolve(
        __dirname,
        '../viestinvalitys-service/src/main/resources/static/raportointi',
      ),
      filename: '[name].[contenthash].js',
      publicPath: '/raportointi/',
      clean: true,
    },
    resolve: {
      extensions: ['.tsx', '.ts', '.js'],
      alias: {
        '@': path.resolve(__dirname, 'src'),
      },
    },
    module: {
      rules: [
        {
          test: /\.m?js$/,
          resolve: { fullySpecified: false },
        },
        {
          test: /\.tsx?$/,
          use: 'ts-loader',
          exclude: /node_modules/,
        },
        {
          test: /\.css$/,
          use: ['style-loader', 'css-loader'],
        },
      ],
    },
    plugins: [
      new HtmlWebpackPlugin({
        template: './src/index.html',
        filename: 'index.html',
      }),
      new webpack.IgnorePlugin({
        resourceRegExp: /^\.\/locale$/,
        contextRegExp: /moment$/,
      }),
    ],
    devServer: {
      port: 3000,
      historyApiFallback: {
        index: '/raportointi/index.html',
        rewrites: [{ from: /^\/raportointi/, to: '/raportointi/index.html' }],
      },
      server: (() => {
        const certs = isDev ? loadDevCerts() : false;
        return certs ? { type: 'https', options: certs } : 'http';
      })(),
      proxy: [
        {
          context: ['/raportointi/v1', '/raportointi/login', '/raportointi/login/'],
          target: 'http://localhost:8081',
          changeOrigin: true,
          secure: false,
        },
        {
          context: ['/lokalisointi'],
          target: 'https://virkailija.hahtuvaopintopolku.fi',
          changeOrigin: true,
          secure: false,
        },
        {
          context: ['/organisaatio-service'],
          target: 'https://virkailija.hahtuvaopintopolku.fi',
          changeOrigin: true,
          secure: false,
        },
        {
          context: ['/virkailija-raamit'],
          target: 'https://virkailija.hahtuvaopintopolku.fi',
          changeOrigin: true,
          secure: false,
        },
      ],
    },
    performance: {
      hints: false,
    },
    optimization: {
      splitChunks: {
        chunks: 'all',
        cacheGroups: {
          mui: {
            test: /[\\/]node_modules[\\/]@mui[\\/]/,
            name: 'vendor-mui',
            chunks: 'all',
            priority: 20,
          },
          moment: {
            test: /[\\/]node_modules[\\/]moment/,
            name: 'vendor-moment',
            chunks: 'all',
            priority: 20,
          },
          vendor: {
            test: /[\\/]node_modules[\\/]/,
            name: 'vendor',
            chunks: 'all',
            priority: 10,
          },
        },
      },
    },
  };
};
