'use client';

import { Suspense } from 'react';
import { useHydration } from '../hooks/useHydration';

// pieni kikkailu SSR hydration-ongelman välttämiseksi
// ks. https://francoisbest.com/posts/2023/displaying-local-times-in-nextjs
const LocalDateTime = ({ date }: { date: string }) => {
  const hydrated = useHydration();
  return (
    <Suspense key={hydrated ? 'local' : 'utc'}>
      <time dateTime={new Date(date).toISOString()}>
        {new Date(date).toLocaleString()}
        {hydrated ? '' : ' (UTC)'}
      </time>
    </Suspense>
  );
};
export default LocalDateTime;
