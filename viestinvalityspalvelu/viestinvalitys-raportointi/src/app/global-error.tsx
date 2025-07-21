'use client';

import { useTranslations } from 'next-intl';
import { MainContainer } from './components/MainContainer';

export default function GlobalError() {
  const t = useTranslations();
  return (
    <html>
      <body>
        <MainContainer>
          <h2>{t('error.otsikko')}</h2>
          <p>{t('error.teksti')}</p>
        </MainContainer>
      </body>
    </html>
  );
}
