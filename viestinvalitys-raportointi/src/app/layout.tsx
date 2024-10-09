import { NextIntlClientProvider} from 'next-intl';
import { getLocale, getMessages, getTranslations } from 'next-intl/server';
import { AppRouterCacheProvider } from '@mui/material-nextjs/v14-appRouter';
import { CssBaseline } from '@mui/material';
import ReactQueryClientProvider from './components/react-query-client-provider';
import type { Metadata } from 'next';
import { OphNextJsThemeProvider } from '@opetushallitus/oph-design-system/next/theme';
import { PageLayout } from './components/PageLayout';
import NavAppBar from './components/NavAppBar';
import { LanguageCode } from './lib/types';

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations();
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
  const locale = (await getLocale()) as LanguageCode;
  const messages = await getMessages();
  return (
    <html lang={locale}>
      <body>
        <AppRouterCacheProvider>
         <NextIntlClientProvider messages={messages}>
          <ReactQueryClientProvider>
            <OphNextJsThemeProvider variant="oph" lang={locale}>
              <CssBaseline />
                <PageLayout header={<NavAppBar />}>{children}</PageLayout>
            </OphNextJsThemeProvider>
          </ReactQueryClientProvider>
          </NextIntlClientProvider>
        </AppRouterCacheProvider>
      </body>
    </html>
  );
}
