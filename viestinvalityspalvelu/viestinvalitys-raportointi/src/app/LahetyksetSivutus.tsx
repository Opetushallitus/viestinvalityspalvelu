'use client';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import { useTranslations } from 'next-intl';
import { NUQS_DEFAULT_OPTIONS } from './lib/constants';
import { useQueryState } from 'nuqs';
import { OphButton } from '@opetushallitus/oph-design-system';

const LahetyksetSivutus = ({
  sivutusAlkaenParam,
}: {
  sivutusAlkaenParam?: string;
}) => {
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  const [seuraavatAlkaen, setSeuraavatAlkaen] = useQueryState(
    'seuraavatAlkaen',
    NUQS_DEFAULT_OPTIONS,
  );
  const t = useTranslations();
  return sivutusAlkaenParam ? (
    <OphButton
      variant="text"
      onClick={() => setSeuraavatAlkaen(sivutusAlkaenParam ?? null)}
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
