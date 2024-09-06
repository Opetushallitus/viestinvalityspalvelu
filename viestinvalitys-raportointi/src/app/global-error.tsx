'use client';

import { useTranslation } from './i18n/clientLocalization';

export default function GlobalError() {
  const { t } = useTranslation();
  return (
    <html>
      <body>
        <h2>{t('error.otsikko')}</h2>
        <p>{t('error.teksti')}</p>
      </body>
    </html>
  );
}
