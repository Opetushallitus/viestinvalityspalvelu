'use client';

import { useEffect } from 'react';
import { useTranslation } from './i18n/clientLocalization';
import { MainContainer } from './components/MainContainer';

export default function Error({
  error,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    console.error(error);
  }, [error]);
  const { t } = useTranslation();
  return (
    <MainContainer>
      <div>
        <h2>{t('error.otsikko')}</h2>
        <p>{t('error.teksti')}</p>
      </div>
    </MainContainer>
  );
}
