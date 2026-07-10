import { useTranslation } from 'react-i18next';
import { MainContainer } from '../components/MainContainer';

export default function NotFoundPage() {
  const { t } = useTranslation();
  return (
    <MainContainer>
      <h1>{t('error.notfound.otsikko')}</h1>
      <p>{t('error.notfound.teksti')}</p>
    </MainContainer>
  );
}
