import { lazy, Suspense } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { OphThemeProvider } from './components/OphThemeProvider';
import { PageLayout } from './components/PageLayout';
import NavAppBar from './components/NavAppBar';
import Loading from './components/Loading';

const LahetyksetPage = lazy(() => import('./pages/LahetyksetPage'));
const LahetysPage = lazy(() => import('./pages/LahetysPage'));
const NotFoundPage = lazy(() => import('./pages/NotFoundPage'));

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <OphThemeProvider>
        <BrowserRouter basename="/raportointi">
          <PageLayout header={<NavAppBar />}>
            <Suspense fallback={<Loading />}>
              <Routes>
                <Route path="/" element={<LahetyksetPage />} />
                <Route path="/lahetys/:tunniste" element={<LahetysPage />} />
                <Route path="/404" element={<NotFoundPage />} />
                <Route path="*" element={<Navigate to="/404" replace />} />
              </Routes>
            </Suspense>
          </PageLayout>
        </BrowserRouter>
      </OphThemeProvider>
    </QueryClientProvider>
  );
}
