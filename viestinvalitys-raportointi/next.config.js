/** @type {import('next').NextConfig} */

const cspHeader = `
    default-src 'self';
    script-src 'self' ${process.env.VIRKAILIJA_URL} 'unsafe-eval' 'unsafe-inline';
    style-src 'self'  ${process.env.VIRKAILIJA_URL} https://fonts.googleapis.com 'unsafe-inline';
    img-src 'self' blob: data:;
    font-src 'self' https://fonts.gstatic.com;
    object-src 'none';
    base-uri 'self';
    form-action 'self';
    frame-ancestors 'none';
    block-all-mixed-content;
    upgrade-insecure-requests;
`;
const isProd = process.env.NODE_ENV === 'production';
const nextConfig = {
  experimental: {
    missingSuspenseWithCSRBailout: false,
  },
  output: 'standalone',
  basePath: isProd ? '/raportointi' : undefined,
  assetPrefix: isProd ? '/static' : undefined,
  async headers() {
    return [
      {
        source: '/(.*)',
        headers: [
          {
            key: 'Content-Security-Policy',
            value: cspHeader.replace(/\n/g, ''),
          },
        ],
      },
    ];
  },
};

module.exports = nextConfig;
