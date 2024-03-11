import type { Metadata } from 'next'
import { AppRouterCacheProvider } from '@mui/material-nextjs/v13-appRouter';
import './globals.css'
import Link from 'next/link';
import { Roboto } from 'next/font/google'
import HomeIconLink from './components/HomeIconLink';
import OrganisaatioSelect from './components/OrganisaatioSelect';
import { fetchOrganisaatioHierarkia } from './lib/data';
import { Organisaatio } from './lib/types';
 
const roboto = Roboto({
  weight: ['400', '500'],
  display: 'swap',
  subsets: ['latin'],
})

export const metadata: Metadata = {
  title: 'Viestinvälityspalvelun raportointi',
  description: 'Viestinvälityspalvelun raportointikäyttöliittymä',
}

export default async function RootLayout({
  children,
}: {
  children: React.ReactNode
}) {
  const orgData = await fetchOrganisaatioHierarkia()
  const organisaatiot: Organisaatio[] = orgData
  console.log(orgData.length)
  return (
    <html lang="fi" className={roboto.className}>
      <body>
        <header>
          <nav>
            <HomeIconLink />
            <OrganisaatioSelect organisaatiot={organisaatiot}/>
          </nav>
        </header>
        <AppRouterCacheProvider options={{ enableCssLayer: true }}>{children}</AppRouterCacheProvider>
      </body>
    </html>
  )
}
