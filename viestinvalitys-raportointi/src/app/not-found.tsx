import Link from 'next/link';
import { initTranslations } from './i18n/localization';

export default async function NotFound() {
  const { t } = await initTranslations();
  return (
    <div>
      <h2>{t('error.404.otsikko')}</h2>
      <p>{t('error.404.teksti')}</p>
      <Link href="/">{t('yleinen.palaa-etusivulle')}</Link>
    </div>
  );
}
