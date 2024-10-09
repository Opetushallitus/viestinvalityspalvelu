'use client';

import { MainContainer } from '@/app/components/MainContainer';
import { useTranslations } from 'next-intl';
import { useEffect } from 'react';

export default function Error({
  error,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    console.error(error);
  }, [error]);
  const t = useTranslations();
  return (
    <MainContainer>
      <div>
        <h2>{t('error.otsikko')}</h2>
        <p>{t('error.teksti')}</p>
      </div>
    </MainContainer>
  );
}
