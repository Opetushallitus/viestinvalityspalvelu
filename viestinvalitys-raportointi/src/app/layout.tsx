import type { Metadata } from 'next';
import { AppRouterCacheProvider } from '@mui/material-nextjs/v14-appRouter';
import './globals.css';
import { Roboto } from 'next/font/google';
import NavAppBar from './components/NavAppBar';
import { CssBaseline, ThemeProvider } from '@mui/material';
import theme from './theme';
import ReactQueryClientProvider from './components/react-query-client-provider';
import { PageLayout } from './components/PageLayout';

const roboto = Roboto({
  weight: ['400', '500'],
  display: 'swap',
  subsets: ['latin'],
});

export const metadata: Metadata = {
  title: 'Viestinvälityspalvelun raportointi',
  description: 'Viestinvälityspalvelun raportointikäyttöliittymä',
};

export default async function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="fi" className={roboto.className}>
      <body>
        <AppRouterCacheProvider>
          <ReactQueryClientProvider>
            <ThemeProvider theme={theme}>
              <CssBaseline />
              <PageLayout header={<NavAppBar />}>
                {children}
              </PageLayout>              
            </ThemeProvider>
          </ReactQueryClientProvider>
        </AppRouterCacheProvider>
      </body>
    </html>
  );
}
