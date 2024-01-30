/** @type {import('next').NextConfig} */
const isProd = process.env.NODE_ENV === 'production'
const nextConfig = {
  output: 'standalone',
  basePath: isProd ? '/raportointi' : undefined,
  assetPrefix: isProd? '/static' : undefined,
}

module.exports = nextConfig
