/** @type {import('next').NextConfig} */
import createNextIntlPlugin from 'next-intl/plugin';

const cspHeader = `
    default-src 'self';
    script-src 'self' 'unsafe-eval' 'unsafe-inline';
    style-src 'self' 'unsafe-inline';
    img-src 'self' blob: data:;
    font-src 'self';
    object-src 'none';
    base-uri 'self';
    form-action 'self';
    frame-ancestors 'none';
    block-all-mixed-content;
    upgrade-insecure-requests;
`;
const isProd = process.env.NODE_ENV === 'production';

const withNextIntl = createNextIntlPlugin();

const nextConfig = {
  experimental: {
    missingSuspenseWithCSRBailout: false,
    esmExternals: false,
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

export default withNextIntl(nextConfig);
