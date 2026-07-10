import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router-dom';
import { OphButton } from '@opetushallitus/oph-design-system';

const LahetyksetSivutus = ({ sivutusAlkaenParam }: { sivutusAlkaenParam?: string | null }) => {
  const [, setSearchParams] = useSearchParams();
  const { t } = useTranslation();

  const handleClick = () => {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      if (sivutusAlkaenParam) {
        next.set('seuraavatAlkaen', sivutusAlkaenParam);
      } else {
        next.delete('seuraavatAlkaen');
      }
      return next;
    });
  };

  return sivutusAlkaenParam ? (
    <OphButton
      variant="text"
      onClick={handleClick}
      aria-label={t('yleinen.sivutus.seuraavat')}
      size="large"
      endIcon={<ChevronRightIcon />}
    >
      {t('yleinen.sivutus.seuraavat')}
    </OphButton>
  ) : (
    <></>
  );
};

export default LahetyksetSivutus;
