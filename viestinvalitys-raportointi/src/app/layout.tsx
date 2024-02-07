import type { Metadata } from 'next'
import { AppRouterCacheProvider } from '@mui/material-nextjs/v13-appRouter';
import './globals.css'
import Link from 'next/link';
import { Roboto } from 'next/font/google'
import HomeIconLink from './components/HomeIconLink';
 
const roboto = Roboto({
  weight: ['400', '500'],
  display: 'swap',
  subsets: ['latin'],
})

export const metadata: Metadata = {
  title: 'Viestinvälityspalvelun raportointi',
  description: 'Viestinvälityspalvelun raportointikäyttöliittymä',
}

export default function RootLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <html lang="fi" className={roboto.className}>
      <body>
        <header>
          <nav>
            <HomeIconLink />
            {/* <Link href="/">Home</Link> */}
          </nav>
        </header>
        <AppRouterCacheProvider options={{ enableCssLayer: true }}>{children}</AppRouterCacheProvider>
      </body>
    </html>
  )
}
