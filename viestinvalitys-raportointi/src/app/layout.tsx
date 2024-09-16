import { AppRouterCacheProvider } from '@mui/material-nextjs/v14-appRouter';
import NavAppBar from './components/NavAppBar';
import { CssBaseline } from '@mui/material';
import ReactQueryClientProvider from './components/react-query-client-provider';
import { PageLayout } from './components/PageLayout';
import { getLocale, initTranslations } from './i18n/localization';
import type { Metadata } from 'next';
import { LocaleProvider } from './i18n/locale-provider';
import { OphNextJsThemeProvider } from '@opetushallitus/oph-design-system/next/theme';

export async function generateMetadata(): Promise<Metadata> {
  const { t } = await initTranslations();
  return {
    title: t('metadata.title'),
    description: t('metadata.description'),
  };
}

export default async function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const locale = await getLocale();
  return (
    <html lang={locale}>
      <body>
        <AppRouterCacheProvider>
          <ReactQueryClientProvider>
            <OphNextJsThemeProvider variant="oph" lang={locale}>
              <CssBaseline />
              <LocaleProvider value={locale}>
                <PageLayout header={<NavAppBar />}>{children}</PageLayout>
              </LocaleProvider>
            </OphNextJsThemeProvider>
          </ReactQueryClientProvider>
        </AppRouterCacheProvider>
      </body>
    </html>
  );
}
