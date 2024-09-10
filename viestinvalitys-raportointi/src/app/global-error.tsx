'use client';

import { MainContainer } from './components/MainContainer';
import { useTranslation } from './i18n/clientLocalization';

export default function GlobalError() {
  const { t } = useTranslation();
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
