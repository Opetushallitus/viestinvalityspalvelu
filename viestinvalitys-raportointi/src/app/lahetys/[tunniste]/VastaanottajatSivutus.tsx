'use client';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import { useTranslations } from 'next-intl';
import { useQueryState } from 'nuqs';
import { NUQS_DEFAULT_OPTIONS } from '@/app/lib/constants';
import { OphButton } from '@opetushallitus/oph-design-system';

const VastaanottajatSivutus = ({
  sivutusAlkaenParam,
}: {
  sivutusAlkaenParam?: string;
}) => {
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  const [alkaen, setAlkaen] = useQueryState(
    'alkaen',
    NUQS_DEFAULT_OPTIONS,
  );
  const t = useTranslations();
  return sivutusAlkaenParam ? (
    <OphButton
      variant="text"
      onClick={() => setAlkaen(sivutusAlkaenParam ?? null)}
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
