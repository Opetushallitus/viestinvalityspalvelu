'use client';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import { Button } from '@mui/material';
import Link from 'next/link';
import { useTranslations } from 'next-intl';
import useQueryParams from './hooks/useQueryParams';

const LahetyksetSivutus = ({
  seuraavatAlkaen,
}: {
  seuraavatAlkaen?: string;
}) => {
  const { createQueryString } = useQueryParams();
  const t = useTranslations();
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
