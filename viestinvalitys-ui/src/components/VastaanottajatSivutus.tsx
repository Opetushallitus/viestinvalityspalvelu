import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router-dom';
import { OphButton } from '@opetushallitus/oph-design-system';

const VastaanottajatSivutus = ({ sivutusAlkaenParam }: { sivutusAlkaenParam?: string | null }) => {
  const [, setSearchParams] = useSearchParams();
  const { t } = useTranslation();

  const handleClick = () => {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      if (sivutusAlkaenParam) {
        next.set('alkaen', sivutusAlkaenParam);
      } else {
        next.delete('alkaen');
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

export default VastaanottajatSivutus;
