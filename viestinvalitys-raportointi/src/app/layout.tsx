import type { Metadata } from 'next'
import { AppRouterCacheProvider } from '@mui/material-nextjs/v13-appRouter';
import './globals.css'
import { Roboto } from 'next/font/google'
import NavAppBar from './components/NavAppBar';
import { ThemeProvider } from '@mui/material';
import theme from './theme';
 
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
    return (
    <html lang="fi" className={roboto.className}>
      <body>
        <header>
          <nav>
            <NavAppBar/>
          </nav>
        </header>
        <ThemeProvider theme={theme}>
          <AppRouterCacheProvider options={{ enableCssLayer: true }}>{children}</AppRouterCacheProvider>
        </ThemeProvider>
      </body>
    </html>
  )
}
