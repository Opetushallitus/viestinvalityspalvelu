import Link from 'next/link';
import { useTranslations } from 'next-intl';

export default function NotFound() {
  const t = useTranslations();
  return (
    <div>
      <h2>{t('error.notfound.otsikko')}</h2>
      <p>{t('error.notfound.teksti')}</p>
      <Link href="/">{t('yleinen.palaa-etusivulle')}</Link>
    </div>
  );
}
