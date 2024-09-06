'use client';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import { Button } from '@mui/material';
import Link from 'next/link';
import useQueryParams from './hooks/useQueryParams';
import { useTranslation } from './i18n/clientLocalization';

const LahetyksetSivutus = ({
  seuraavatAlkaen,
}: {
  seuraavatAlkaen?: string;
}) => {
  const { createQueryString } = useQueryParams();
  const { t } = useTranslation();
  return seuraavatAlkaen ? (

    <Button
      component={Link}
      href={'/?' + createQueryString('seuraavatAlkaen', seuraavatAlkaen)}
      aria-label={t('yleinen.sivutus.seuraavat')}
      size="large"
      endIcon={<ChevronRightIcon />}
      prefetch={false}
    >
      {t('yleinen.sivutus.seuraavat')}
    </Button>
  ) : (
    <></>
  );
};

export default LahetyksetSivutus;
